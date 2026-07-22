package com.stoxsim.trade.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.trade.domain.Trade;

public record TradeResponse(
    UUID id,
    UUID orderId,
    String marketRegion,
    String exchange,
    String symbol,
    OrderSide side,
    long quantity,
    BigDecimal price,
    BigDecimal grossValue,
    BigDecimal charges,
    BigDecimal brokerage,
    BigDecimal stt,
    BigDecimal exchangeCharges,
    BigDecimal gst,
    BigDecimal sebiCharges,
    BigDecimal stampDuty,
    BigDecimal dpCharges,
    BigDecimal netCashEffect,
    String chargeScheduleVersion,
    boolean chargesSimulated,
    Instant executedAt
) {
    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
            trade.getId(),
            trade.getOrder().getId(),
            trade.getInstrument().getMarketRegion().name(),
            trade.getInstrument().getExchange().name(),
            trade.getInstrument().getTradingSymbol(),
            trade.getSide(),
            trade.getQuantity(),
            trade.getPrice(),
            trade.getGrossValue(),
            trade.getCharges(),
            trade.getBrokerage(),
            trade.getStt(),
            trade.getExchangeCharges(),
            trade.getGst(),
            trade.getSebiCharges(),
            trade.getStampDuty(),
            trade.getDpCharges(),
            trade.getNetCashEffect(),
            trade.getChargeScheduleVersion(),
            trade.isChargesSimulated(),
            trade.getExecutedAt()
        );
    }
}
