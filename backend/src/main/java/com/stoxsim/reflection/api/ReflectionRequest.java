package com.stoxsim.reflection.api;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReflectionRequest(
    UUID accountId, UUID orderId,
    @NotBlank @Size(max = 2000) String reason,
    @Size(max = 2000) String assumptions,
    @Size(max = 2000) String risks,
    @Size(max = 2000) String expectedOutcome,
    @Size(max = 2000) String reflection,
    @jakarta.validation.Valid StrategyPlan plan,
    Boolean completeReview
) {
    // Existing native clients and service callers remain compatible.
    public ReflectionRequest(UUID accountId, UUID orderId, String reason, String assumptions,
        String risks, String expectedOutcome, String reflection) {
        this(accountId, orderId, reason, assumptions, risks, expectedOutcome, reflection, null, null);
    }
}
