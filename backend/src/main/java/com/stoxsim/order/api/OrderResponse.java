package com.stoxsim.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.OrderStatus;
import com.stoxsim.order.domain.OrderType;
import com.stoxsim.order.domain.OrderValidity;
import com.stoxsim.order.domain.PaperOrder;
import com.stoxsim.order.domain.ProductType;

public record OrderResponse(
    UUID id,
    String marketRegion,
    String exchange,
    String symbol,
    String currency,
    OrderSide side,
    OrderType orderType,
    ProductType productType,
    OrderValidity validity,
    OrderStatus status,
    long quantity,
    BigDecimal limitPrice,
    BigDecimal reservedCash,
    BigDecimal executionPrice,
    BigDecimal executedValue,
    String rejectionReason,
    LocalDate submittedForDate,
    Instant createdAt,
    Instant updatedAt,
    Instant executedAt
) {
    public static OrderResponse from(PaperOrder order) {
        return new OrderResponse(
            order.getId(),
            order.getInstrument().getMarketRegion().name(),
            order.getInstrument().getExchange().name(),
            order.getInstrument().getTradingSymbol(),
            order.getInstrument().getCurrency(),
            order.getSide(),
            order.getOrderType(),
            order.getProductType(),
            order.getValidity(),
            order.getStatus(),
            order.getQuantity(),
            order.getLimitPrice(),
            order.getReservedCash(),
            order.getExecutionPrice(),
            order.getExecutedValue(),
            order.getRejectionReason(),
            order.getSubmittedForDate(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            order.getExecutedAt()
        );
    }
}
