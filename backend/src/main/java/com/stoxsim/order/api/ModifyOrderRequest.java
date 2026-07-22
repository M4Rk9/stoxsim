package com.stoxsim.order.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

public record ModifyOrderRequest(
    @Positive long quantity,
    @DecimalMin(value = "0.0001") BigDecimal limitPrice
) {
}
