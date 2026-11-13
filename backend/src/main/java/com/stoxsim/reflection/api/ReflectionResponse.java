package com.stoxsim.reflection.api;

import java.time.Instant;
import java.util.UUID;

public record ReflectionResponse(UUID id, UUID accountId, UUID orderId, String reason,
    String assumptions, String risks, String expectedOutcome, String reflection,
    Instant createdAt, Instant updatedAt, StrategyPlan plan, Instant reviewedAt, LinkedOrder linkedOrder) {
    public record LinkedOrder(String status, String side, long quantity, java.math.BigDecimal executionPrice,
        String currency, String exchange, String symbol, Instant executedAt) { }
}
