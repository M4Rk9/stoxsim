package com.stoxsim.order.api;

import java.math.BigDecimal;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.OrderType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PlaceOrderRequest(
    @NotNull MarketRegion marketRegion,
    @NotNull MarketExchange exchange,
    @NotBlank String symbol,
    @NotNull OrderSide side,
    @NotNull OrderType orderType,
    @Positive long quantity,
    @DecimalMin(value = "0.0001") BigDecimal limitPrice
) {
}
