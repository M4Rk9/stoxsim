package com.stoxsim.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AccountEventResponse(
    UUID id,
    String type,
    String detail,
    Instant createdAt
) {
}
