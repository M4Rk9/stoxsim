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
    @Size(max = 2000) String reflection
) { }
