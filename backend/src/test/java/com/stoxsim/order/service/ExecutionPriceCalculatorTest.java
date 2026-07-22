package com.stoxsim.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.config.TradingProperties;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.OrderType;

class ExecutionPriceCalculatorTest {

    private final ExecutionPriceCalculator calculator = calculator();

    @Test
    void appliesDisadvantageousSlippageToMarketOrders() {
        Quote quote = quote("100.00", "99.90", "100.10");

        assertThat(calculator.reservationPrice(
            OrderSide.BUY,
            OrderType.MARKET,
            null,
            quote
        )).isEqualByComparingTo("100.1501");
        assertThat(calculator.reservationPrice(
            OrderSide.SELL,
            OrderType.MARKET,
            null,
            quote
        )).isEqualByComparingTo("99.8501");
    }

    @Test
    void limitReservationUsesTheLimitPrice() {
        assertThat(calculator.reservationPrice(
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("95.00"),
            quote("100.00", null, null)
        )).isEqualByComparingTo("95.0000");
    }

    private ExecutionPriceCalculator calculator() {
        var properties = new TradingProperties();
        properties.setSlippageBasisPoints(5);
        return new ExecutionPriceCalculator(properties);
    }

    private Quote quote(String last, String bid, String ask) {
        Instant now = Instant.now();
        return new Quote(
            new InstrumentKey("UPSTOX", "NSE_EQ|TEST", MarketRegion.INDIA),
            new BigDecimal(last),
            bid == null ? null : new BigDecimal(bid),
            ask == null ? null : new BigDecimal(ask),
            null,
            null,
            null,
            null,
            null,
            null,
            now,
            now
        );
    }
}
