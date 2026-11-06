package com.stoxsim.notification;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class FcmHttpTest {
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private FcmHttp billing;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
        billing = new FcmHttp(new ObjectMapper(), HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build(), Duration.ofMillis(600));
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path))
            .timeout(Duration.ofSeconds(10)).GET().build();
    }

    private void unavailable(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
            assertThat(error.getStatusCode().value()).isEqualTo(502);
            assertThat(error.getReason()).isEqualTo(FcmHttp.unavailable().getReason());
            assertThat(error.getCause()).isNull();
        });
    }

    @Test
    void bodyStallingAfterSuccessfulHeadersStillExpires() {
        var headersSent = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.createContext("/stalled", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                headersSent.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        });
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> unavailable(() -> billing.send(request("/stalled"))));
            assertThat(headersSent.getCount()).isZero();
        } finally {
            release.countDown();
        }
    }

    @Test
    void oversizedBodyIsRejectedWithoutWaitingForEndOfStream() {
        var release = new CountDownLatch(1);
        server.createContext("/large", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(new byte[FcmHttp.MAX_RESPONSE_BYTES + 1]);
                exchange.getResponseBody().flush();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            } catch (java.io.IOException ignored) { /* Client cancels the oversized exchange. */ }
        });
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> unavailable(() -> billing.send(request("/large"))));
        } finally {
            release.countDown();
        }
    }

    @Test
    void rejectsOversizedChunkBeforeReadingAndCancelsUpstream() {
        var subscriber = new FcmHttp.BoundedBodySubscriber();
        var cancelled = new AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription() {
            public void request(long count) { }
            public void cancel() { cancelled.set(true); }
        });
        ByteBuffer oversized = ByteBuffer.allocate(FcmHttp.MAX_RESPONSE_BYTES + 1);
        subscriber.onNext(List.of(oversized));
        assertThat(oversized.position()).isZero();
        assertThat(cancelled).isTrue();
        assertThat(subscriber.getBody().toCompletableFuture()).isCompletedExceptionally();
    }

    @Test
    void cancellationBeforeHeadersAlsoCancelsLateSubscription() {
        var subscriber = new FcmHttp.BoundedBodySubscriber();
        var cancelled = new AtomicBoolean();
        subscriber.cancel();
        subscriber.onSubscribe(new Flow.Subscription() {
            public void request(long count) { fail("Cancelled response must not request bytes"); }
            public void cancel() { cancelled.set(true); }
        });
        assertThat(cancelled).isTrue();
    }

    @Test
    void validObjectsAndEmptySuccessResponsesRemainSupported() {
        server.createContext("/valid", exchange -> {
            try (exchange) {
                byte[] bytes = "{\"status\":\"verified\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.createContext("/empty", exchange -> {
            try (exchange) { exchange.sendResponseHeaders(204, -1); }
        });
        assertThat(billing.send(request("/valid")).body().path("status").asText()).isEqualTo("verified");
        assertThat(billing.send(request("/empty")).body().isObject()).isTrue();
    }

    @Test
    void providerErrorAndInvalidJsonCannotLeakResponseContent() {
        String privateMessage = "merchant credential private-value";
        server.createContext("/error", exchange -> {
            try (exchange) {
                byte[] bytes = privateMessage.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(403, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.createContext("/malformed", exchange -> {
            try (exchange) {
                byte[] bytes = ("{\"secret\":\"" + privateMessage).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        unavailable(() -> billing.send(request("/error")));
        unavailable(() -> billing.send(request("/malformed")));
    }
}
