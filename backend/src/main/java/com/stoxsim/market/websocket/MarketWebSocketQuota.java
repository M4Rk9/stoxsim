package com.stoxsim.market.websocket;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/** Per-instance budgets: each JVM bounds its own sockets, broker work and memory. */
@Component
public class MarketWebSocketQuota {
    private final MarketWebSocketLimits limits;
    private final MeterRegistry metrics;
    private final LongSupplier time;
    private final Map<String, Connection> connections = new HashMap<>();
    private final Map<String, UserBudget> users = new HashMap<>();
    private final Bucket rawGlobal;
    private final Bucket stompGlobal;

    @Autowired
    public MarketWebSocketQuota(MarketWebSocketLimits limits, MeterRegistry metrics) {
        this(limits, metrics, System::nanoTime);
    }

    MarketWebSocketQuota(MarketWebSocketLimits limits, MeterRegistry metrics, LongSupplier time) {
        this.limits = limits;
        this.metrics = metrics;
        this.time = time;
        rawGlobal = new Bucket(limits.globalFramesPerSecond(), time.getAsLong());
        stompGlobal = new Bucket(limits.globalFramesPerSecond(), time.getAsLong());
        metrics.gauge("stoxsim.websocket.connections", this, MarketWebSocketQuota::activeConnections);
    }

    public synchronized boolean open(WebSocketSession socket) {
        var address = socket.getRemoteAddress();
        String ip = address == null ? "unknown" : address.getHostString();
        if (connections.containsKey(socket.getId()) || connections.size() >= limits.maxConnections()
            || connections.values().stream().filter(c -> c.ip.equals(ip)).count() >= limits.connectionsPerIp()) {
            rejected("connection");
            return false;
        }
        connections.put(socket.getId(), new Connection(socket, ip, time.getAsLong()));
        return true;
    }

    public synchronized boolean rawFrame(String id) {
        Connection connection = connections.get(id);
        long now = time.getAsLong();
        return connection != null && !connection.closing
            && connection.raw.take(now) && rawGlobal.take(now);
    }

    public synchronized boolean stompFrame(String id) {
        Connection connection = connections.get(id);
        long now = time.getAsLong();
        if (connection == null || connection.closing || !connection.stomp.take(now) || !stompGlobal.take(now)) return false;
        if (connection.user == null) return true;
        UserBudget budget = users.get(connection.user);
        budget.lastUsed = now;
        return budget.frames.take(now);
    }

    public synchronized boolean authenticate(String id, String user, Instant expiresAt) {
        Connection connection = connections.get(id);
        if (connection == null || connection.closing || connection.user != null || user == null || user.isBlank()) return false;
        long now = time.getAsLong();
        UserBudget budget = users.computeIfAbsent(user, key -> new UserBudget(now));
        if (budget.connections >= limits.connectionsPerUser() || !budget.frames.take(now)) return false;
        budget.connections++;
        budget.lastUsed = now;
        connection.user = user;
        connection.expiresAt = expiresAt;
        return true;
    }

    public synchronized boolean subscribe(String id, String subscription) {
        Connection connection = connections.get(id);
        if (connection == null || connection.closing || connection.user == null
            || connection.subscription != null || subscription == null || subscription.isBlank() || subscription.length() > 64) return false;
        // The product has one quote topic. Duplicate/different IDs cannot multiply delivery.
        connection.subscription = subscription;
        return true;
    }

    public synchronized void unsubscribe(String id, String subscription) {
        Connection connection = connections.get(id);
        if (connection != null && subscription != null && subscription.equals(connection.subscription)) connection.subscription = null;
    }

    public synchronized void closed(String id) {
        Connection connection = connections.remove(id);
        if (connection != null && connection.user != null) {
            UserBudget budget = users.get(connection.user);
            budget.connections--;
            budget.lastUsed = time.getAsLong();
        }
    }

    public void terminate(String id, CloseStatus status, String reason) {
        WebSocketSession socket;
        synchronized (this) {
            Connection connection = connections.get(id);
            if (connection == null) return;
            connection.closing = true;
            socket = connection.socket;
        }
        rejected(reason);
        try { socket.close(status); }
        catch (IOException failure) {
            // Keep the reservation until transport closure; the timer retries closure.
            metrics.counter("stoxsim.websocket.close_failures").increment();
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void expireUnauthenticated() {
        List<String> expired;
        synchronized (this) {
            long now = time.getAsLong();
            expired = connections.entrySet().stream()
                .filter(e -> e.getValue().closing || (e.getValue().user == null
                    && now - e.getValue().opened >= limits.authenticationTimeoutSeconds() * 1_000_000_000L)
                    || (e.getValue().expiresAt != null && !e.getValue().expiresAt.isAfter(Instant.now())))
                .map(Map.Entry::getKey).toList();
            // Retain account budgets across reconnects, then discard idle bookkeeping.
            users.values().removeIf(b -> b.connections == 0 && now - b.lastUsed >= 60_000_000_000L);
        }
        expired.forEach(id -> terminate(id, CloseStatus.POLICY_VIOLATION, "authentication_timeout"));
    }

    public synchronized int activeConnections() { return connections.size(); }

    private void rejected(String reason) {
        metrics.counter("stoxsim.websocket.rejections", "reason", reason).increment();
    }

    private final class Connection {
        final WebSocketSession socket;
        final String ip;
        final long opened;
        final Bucket raw;
        final Bucket stomp;
        String user;
        Instant expiresAt;
        String subscription;
        boolean closing;
        Connection(WebSocketSession socket, String ip, long now) {
            this.socket = socket; this.ip = ip; this.opened = now;
            raw = new Bucket(limits.framesPerSecond(), now);
            stomp = new Bucket(limits.framesPerSecond(), now);
        }
    }

    private final class UserBudget {
        final Bucket frames;
        int connections;
        long lastUsed;
        UserBudget(long now) { frames = new Bucket(limits.userFramesPerSecond(), now); lastUsed = now; }
    }

    private static final class Bucket {
        final int capacity;
        double tokens;
        long updated;
        Bucket(int capacity, long now) { this.capacity = capacity; tokens = capacity; updated = now; }
        boolean take(long now) {
            tokens = Math.min(capacity, tokens + Math.max(0, now - updated) / 1_000_000_000.0 * capacity);
            updated = now;
            if (tokens < 1) return false;
            tokens--;
            return true;
        }
    }
}
