package com.stoxsim.reflection.api;

import java.time.LocalDate;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Private learner-authored intentions, never executable order instructions. */
public record StrategyPlan(
    @Pattern(regexp = "|NSE|BSE|NASDAQ|NYSE|NYSE_ARCA|AMEX|CBOE") String exchange,
    @Size(max = 100) @Pattern(regexp = "[A-Z0-9&._-]*") String symbol,
    @Size(max = 200) String horizon,
    @Size(max = 500) String target,
    @Size(max = 500) String invalidation,
    LocalDate reviewOn
) { }
