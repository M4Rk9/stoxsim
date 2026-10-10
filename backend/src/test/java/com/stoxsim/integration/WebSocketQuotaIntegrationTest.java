package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.TokenService;
import com.stoxsim.market.websocket.MarketTickBroadcaster;
import com.stoxsim.market.websocket.MarketWebSocketQuota;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "stoxsim.market-data.upstox.stream-enabled=false",
    "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.polling-enabled=false",
    "stoxsim.portfolio.history.enabled=false",
    "stoxsim.security.rate-limit.enabled=false",
    "stoxsim.security.websocket.max-connections=4",
    "stoxsim.security.websocket.connections-per-ip=4",
    "stoxsim.security.websocket.connections-per-user=2",
    "stoxsim.security.websocket.frames-per-second=5",
    "stoxsim.security.websocket.authentication-timeout-seconds=3"
})
class WebSocketQuotaIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @LocalServerPort int port;
    @Autowired AppUserRepository users;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate db;
    @Autowired MarketWebSocketQuota quota;
    @Autowired SimpMessagingTemplate broker;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ArrayList<SocketClient> sockets = new ArrayList<>();
    String ownerToken, otherToken;

    @BeforeEach void setup() {
        await().atMost(Duration.ofSeconds(10)).until(() -> quota.activeConnections() == 0);
        db.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");
        ownerToken = token("owner"); otherToken = token("other");
    }

    @AfterEach void cleanup() {
        sockets.forEach(socket -> socket.webSocket.abort());
        await().atMost(Duration.ofSeconds(10)).until(() -> quota.activeConnections() == 0);
    }

    private String token(String name) {
        AppUser user = users.saveAndFlush(new AppUser(name + "@example.test", "hash", name));
        return tokens.issueTokenPair(user).accessToken();
    }

    @Test void saturatedConnectionsAreClosedAndAReleasedSlotAdmitsAHealthySubscriber() throws Exception {
        for (int i = 0; i < 4; i++) open();
        await().atMost(Duration.ofSeconds(2)).until(() -> quota.activeConnections() == 4);
        SocketClient overflow = open();
        assertThat(overflow.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(quota.activeConnections()).isEqualTo(4);
        sockets.getFirst().webSocket.sendClose(1000, "done").join();
        await().atMost(Duration.ofSeconds(2)).until(() -> quota.activeConnections() == 3);
        SocketClient healthy = open(); healthy.connect(ownerToken); healthy.subscribe("quotes");
        assertHealthy(healthy);
    }

    @Test void accountQuotaCannotBeBypassedByReusingATokenAndOtherAccountsStillWork() throws Exception {
        for (int i = 0; i < 2; i++) open().connect(ownerToken);
        SocketClient overflow = open(); overflow.send(connectFrame(ownerToken));
        assertThat(overflow.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        await().atMost(Duration.ofSeconds(2)).until(() -> quota.activeConnections() == 2);
        SocketClient healthy = open(); healthy.connect(otherToken); healthy.subscribe("quotes");
        assertHealthy(healthy);
    }

    @Test void heartbeatFloodIsClosedWhileQuotesAndHttpRemainResponsive() throws Exception {
        SocketClient healthy = open(); healthy.connect(otherToken); healthy.subscribe("quotes");
        SocketClient flood = open(); flood.connect(ownerToken);
        for (int i = 0; i < 30 && !flood.closed.isDone(); i++) {
            try { flood.send("\n"); } catch (RuntimeException closed) { break; }
        }
        assertThat(flood.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        assertHealthy(healthy);
    }

    @Test void oversizedFramesCannotReachTheBrokerAndServiceRemainsUsable() throws Exception {
        SocketClient oversized = open(); oversized.send("x".repeat(8193));
        assertThat(oversized.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1009);
        SocketClient healthy = open(); healthy.connect(otherToken); healthy.subscribe("quotes");
        assertHealthy(healthy);
    }

    @Test void unauthenticatedHeartbeatsDoNotKeepASlotAlive() throws Exception {
        SocketClient socket = open();
        long deadline = System.nanoTime() + Duration.ofSeconds(7).toNanos();
        while (!socket.closed.isDone() && System.nanoTime() < deadline) {
            try { socket.send("\n"); } catch (RuntimeException closed) { break; }
            Thread.sleep(400);
        }
        assertThat(socket.closed.get(2, TimeUnit.SECONDS)).isEqualTo(1008);
        await().atMost(Duration.ofSeconds(2)).until(() -> quota.activeConnections() == 0);
    }

    @Test void subscriptionMultiplicationAndCoalescedFrameFloodsAreRejected() throws Exception {
        SocketClient duplicate = open(); duplicate.connect(ownerToken); duplicate.subscribe("first");
        duplicate.send(subscribeFrame("second"));
        assertThat(duplicate.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        await().atMost(Duration.ofSeconds(2)).until(() -> quota.activeConnections() == 0);
        SocketClient coalesced = open(); coalesced.connect(ownerToken);
        coalesced.send((subscribeFrame("quotes") + "UNSUBSCRIBE\nid:quotes\n\n\0").repeat(10));
        assertThat(coalesced.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }

    private void assertHealthy(SocketClient socket) throws Exception {
        // The simple broker has no STOMP receipt support. Probe eventual delivery
        // rather than sleeping or assuming the asynchronous subscription is ready.
        String marker = UUID.randomUUID().toString();
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            broker.convertAndSend(MarketTickBroadcaster.QUOTE_TOPIC, (Object) Map.of("securityProbe", marker));
            String message = socket.messages.poll(100, TimeUnit.MILLISECONDS);
            return message != null && message.contains(marker);
        });
        long started = System.nanoTime();
        var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health/readiness"))
            .timeout(Duration.ofSeconds(3)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    }

    private SocketClient open() {
        SocketClient socket = new SocketClient();
        socket.webSocket = http.newWebSocketBuilder().subprotocols("v12.stomp")
            .header("Origin", "http://localhost:3000")
            .buildAsync(URI.create("ws://localhost:" + port + "/ws/market"), socket).join();
        sockets.add(socket);
        return socket;
    }

    private static String connectFrame(String token) {
        return "CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\nAuthorization:Bearer " + token + "\n\n\0";
    }

    private static String subscribeFrame(String id) {
        return "SUBSCRIBE\nid:" + id + "\ndestination:" + MarketTickBroadcaster.QUOTE_TOPIC + "\n\n\0";
    }

    private static class SocketClient implements WebSocket.Listener {
        WebSocket webSocket;
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final StringBuilder partial = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            partial.append(text);
            if (last) { messages.add(partial.toString()); partial.setLength(0); }
            socket.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            closed.complete(code); return null;
        }
        @Override public void onError(WebSocket socket, Throwable error) { closed.completeExceptionally(error); }
        void send(String frame) { webSocket.sendText(frame, true).join(); }
        void connect(String token) throws Exception {
            send(connectFrame(token));
            assertThat(messages.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED");
        }
        void subscribe(String id) throws Exception {
            send(subscribeFrame(id));
        }
    }
}
