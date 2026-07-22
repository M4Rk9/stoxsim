package com.stoxsim.instrument.service;

import java.time.Duration;
import java.util.UUID;

public record InstrumentSyncResult(
    UUID syncId,
    int accepted,
    int ignored,
    int deactivated,
    Duration duration
) {
}
