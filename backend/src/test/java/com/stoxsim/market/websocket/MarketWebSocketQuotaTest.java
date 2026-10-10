package com.stoxsim.market.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;

class MarketWebSocketQuotaTest {
    private final AtomicLong now = new AtomicLong();
    private final MarketWebSocketLimits limits = new MarketWebSocketLimits(4, 2, 1, 3, 3, 8, 1024, 2);
    private final MarketWebSocketQuota quota = new MarketWebSocketQuota(limits, new SimpleMeterRegistry(), now::get);

    private WebSocketSession socket(String id, int address) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(id);
        when(socket.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0." + address, 1234));
        return socket;
    }

    @Test void enforcesIndependentIpGlobalAndUserCapsAndReleasesIdempotently() {
        assertThat(quota.open(socket("a", 1))).isTrue();
        assertThat(quota.open(socket("b", 1))).isTrue();
        assertThat(quota.open(socket("ip-overflow", 1))).isFalse();
        assertThat(quota.open(socket("c", 2))).isTrue();
        assertThat(quota.open(socket("d", 2))).isTrue();
        assertThat(quota.open(socket("global-overflow", 3))).isFalse();
        assertThat(quota.authenticate("a", "owner", Instant.now().plusSeconds(60))).isTrue();
        assertThat(quota.authenticate("b", "owner", Instant.now().plusSeconds(60))).isFalse();
        quota.closed("a"); quota.closed("a");
        assertThat(quota.authenticate("b", "owner", Instant.now().plusSeconds(60))).isTrue();
        assertThat(quota.open(socket("replacement", 1))).isTrue();
        assertThat(quota.activeConnections()).isEqualTo(4);
    }

    @Test void admissionIsAtomicUnderConcurrentSaturation() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(16)) {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 16; i++) {
                var socket = socket("race-" + i, i + 1);
                results.add(pool.submit(() -> { start.await(); return quota.open(socket); }));
            }
            start.countDown();
            int accepted = 0;
            for (var result : results) if (result.get()) accepted++;
            assertThat(accepted).isEqualTo(4);
        }
        assertThat(quota.activeConnections()).isEqualTo(4);
    }

    @Test void frameBucketsRefillAndAccountBudgetSurvivesReconnects() {
        quota.open(socket("a", 1));
        assertThat(quota.authenticate("a", "owner", null)).isTrue();
        assertThat(quota.stompFrame("a")).isTrue();
        assertThat(quota.stompFrame("a")).isTrue();
        assertThat(quota.stompFrame("a")).isFalse();
        quota.closed("a");
        quota.open(socket("b", 1));
        assertThat(quota.authenticate("b", "owner", null)).isFalse();
        now.addAndGet(1_000_000_000L);
        assertThat(quota.authenticate("b", "owner", null)).isTrue();
        for (int i = 0; i < 3; i++) assertThat(quota.rawFrame("b")).isTrue();
        assertThat(quota.rawFrame("b")).isFalse();
        now.addAndGet(1_000_000_000L);
        assertThat(quota.rawFrame("b")).isTrue();
    }

    @Test void heartbeatTrafficCannotExtendTheAuthenticationDeadline() throws Exception {
        var socket = socket("a", 1);
        quota.open(socket);
        now.addAndGet(2_000_000_000L);
        assertThat(quota.rawFrame("a")).isTrue();
        quota.expireUnauthenticated();
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
        // Reservations remain until the actual close callback, even on close failure.
        assertThat(quota.activeConnections()).isEqualTo(1);
        quota.closed("a");
        assertThat(quota.activeConnections()).isZero();
    }

    @Test void onlyOneSubscriptionIsAllowedAndItCanBeReplacedAfterUnsubscribe() {
        quota.open(socket("a", 1)); quota.authenticate("a", "owner", null);
        assertThat(quota.subscribe("a", "first")).isTrue();
        assertThat(quota.subscribe("a", "second")).isFalse();
        assertThat(quota.subscribe("a", "first")).isFalse();
        quota.unsubscribe("a", "wrong");
        assertThat(quota.subscribe("a", "second")).isFalse();
        quota.unsubscribe("a", "first");
        assertThat(quota.subscribe("a", "second")).isTrue();
    }

    @Test void utf8SizeAndRawRateAreRejectedBeforeTheBroker() throws Exception {
        var delegate = mock(WebSocketHandler.class);
        var handler = new MarketWebSocketHandler(delegate, quota, limits);
        var socket = socket("a", 1);
        handler.afterConnectionEstablished(socket);
        handler.handleMessage(socket, new TextMessage("é".repeat(513)));
        verify(socket).close(CloseStatus.TOO_BIG_TO_PROCESS);
        verify(delegate, never()).handleMessage(any(), any());
        quota.closed("a");
        var flood = socket("b", 1);
        handler.afterConnectionEstablished(flood);
        for (int i = 0; i < 4; i++) handler.handleMessage(flood, new TextMessage("\n"));
        verify(delegate, times(3)).handleMessage(eq(flood), any());
        verify(flood).close(CloseStatus.POLICY_VIOLATION);
        handler.afterConnectionClosed(flood, CloseStatus.POLICY_VIOLATION);
        assertThat(quota.activeConnections()).isZero();
    }
}
