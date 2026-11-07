package com.stoxsim.reflection.api;

import java.time.Instant;
import java.util.UUID;

public record ReflectionResponse(UUID id, UUID accountId, UUID orderId, String reason,
    String assumptions, String risks, String expectedOutcome, String reflection,
    Instant createdAt, Instant updatedAt) { }
