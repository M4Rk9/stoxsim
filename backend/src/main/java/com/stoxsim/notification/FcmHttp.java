package com.stoxsim.notification;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class FcmHttp {
    static final int MAX_RESPONSE_BYTES = 262144;
    private final ObjectMapper json;
    private final HttpClient client;
    private final Duration maximumDuration;

    @Autowired
    public FcmHttp(ObjectMapper json) {
        this(json, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(), Duration.ofSeconds(12));
    }

    FcmHttp(ObjectMapper json, HttpClient client, Duration maximumDuration) {
        if (maximumDuration.isZero() || maximumDuration.isNegative()) {
            throw new IllegalArgumentException("The response deadline must be positive");
        }
        this.json = json;
        this.client = client;
        this.maximumDuration = maximumDuration;
    }

    public Reply send(HttpRequest request) {
        var subscriber = new AtomicReference<BoundedBodySubscriber>();
        var cancelled = new AtomicBoolean();
        CompletableFuture<HttpResponse<byte[]>> responseFuture = null;
        try {
            Duration deadline = request.timeout().filter(value -> value.compareTo(maximumDuration) < 0)
                .orElse(maximumDuration);
            responseFuture = client.sendAsync(request, info -> {
                var body = new BoundedBodySubscriber();
                subscriber.set(body);
                // The deadline can expire while headers are arriving. Cancel late subscribers too.
                if (cancelled.get()) body.cancel();
                return body;
            });
            // HttpRequest.timeout alone does not bound a streamed response after headers arrive.
            // Wait for the entire bounded body, then cancel both the body and HTTP exchange on timeout.
            var response = responseFuture.get(deadline.toNanos(), TimeUnit.NANOSECONDS);
            
            byte[] bytes = response.body();
            JsonNode value = bytes.length == 0 ? json.createObjectNode() : json.readTree(bytes);
            if (value == null || !value.isObject()) throw unavailable();
            return new Reply(response.statusCode(), value);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            // Provider bodies, authorization headers and raw transport errors are never exposed.
            throw unavailable();
        } finally {
            cancelled.set(true);
            var body = subscriber.get();
            if (body != null) body.cancel();
            if (responseFuture != null && !responseFuture.isDone()) responseFuture.cancel(true);
        }
    }

    /** Demand-controlled buffering caps allocation before copying provider response bytes. */
    static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() { return result; }

        @Override
        public synchronized void onSubscribe(Flow.Subscription next) {
            if (subscription != null || result.isDone()) {
                next.cancel();
                return;
            }
            subscription = next;
            next.request(1);
        }

        @Override
        public synchronized void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            long incoming = buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if (incoming > MAX_RESPONSE_BYTES - bytes.size()) {
                cancel();
                return;
            }
            for (ByteBuffer buffer : buffers) {
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public synchronized void onError(Throwable error) {
            result.completeExceptionally(new IOException("Provider response did not complete"));
        }

        @Override
        public synchronized void onComplete() {
            result.complete(bytes.toByteArray());
        }

        synchronized void cancel() {
            if (result.isDone()) return;
            result.completeExceptionally(new IOException("Provider response cancelled"));
            if (subscription != null) subscription.cancel();
        }
    }

    public record Reply(int status, JsonNode body) {}

    public static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Push transport unavailable");
    }
}
