package com.stoxsim.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.stoxsim.market.data.Quote;
import com.stoxsim.order.config.TradingProperties;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.OrderType;
import com.stoxsim.order.domain.PaperOrder;

@Component
public class ExecutionPriceCalculator {

    private static final BigDecimal BASIS_POINT_DIVISOR = new BigDecimal("10000");

    private final BigDecimal slippageRate;

    public ExecutionPriceCalculator(TradingProperties properties) {
        this.slippageRate = BigDecimal.valueOf(properties.getSlippageBasisPoints())
            .divide(BASIS_POINT_DIVISOR, 8, RoundingMode.HALF_UP);
    }

    public BigDecimal reservationPrice(OrderSide side, OrderType type, BigDecimal limit, Quote quote) {
        return type == OrderType.LIMIT ? money(limit) : slippedPrice(side, quote);
    }

    public Optional<BigDecimal> fillPrice(PaperOrder order, Quote quote) {
        BigDecimal reference = referencePrice(order.getSide(), quote);
        if (order.getOrderType() == OrderType.LIMIT) {
            boolean marketable = order.getSide() == OrderSide.BUY
                ? reference.compareTo(order.getLimitPrice()) <= 0
                : reference.compareTo(order.getLimitPrice()) >= 0;
            if (!marketable) {
                return Optional.empty();
            }
            BigDecimal slipped = slippedPrice(order.getSide(), quote);
            return Optional.of(order.getSide() == OrderSide.BUY
                ? slipped.min(order.getLimitPrice())
                : slipped.max(order.getLimitPrice()));
        }
        return Optional.of(slippedPrice(order.getSide(), quote));
    }

    private BigDecimal slippedPrice(OrderSide side, Quote quote) {
        BigDecimal reference = referencePrice(side, quote);
        BigDecimal multiplier = side == OrderSide.BUY
            ? BigDecimal.ONE.add(slippageRate)
            : BigDecimal.ONE.subtract(slippageRate);
        return money(reference.multiply(multiplier));
    }

    private BigDecimal referencePrice(OrderSide side, Quote quote) {
        BigDecimal preferred = side == OrderSide.BUY ? quote.ask() : quote.bid();
        BigDecimal reference = preferred == null ? quote.lastPrice() : preferred;
        if (reference == null || reference.signum() <= 0) {
            throw new TradingValidationException("No executable market price is available");
        }
        return reference;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
