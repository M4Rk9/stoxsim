package com.stoxsim.charge;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.ProductType;

class IndiaDeliveryChargeCalculatorTest {

    private final IndiaDeliveryChargeCalculator calculator =
        new IndiaDeliveryChargeCalculator();

    @Test
    void appliesCurrentSimulatedNseDeliverySchedule() {
        ChargeBreakdown charges = calculator.calculate(
            OrderSide.BUY,
            ProductType.DELIVERY,
            MarketExchange.NSE,
            new BigDecimal("100000.00"),
            LocalDate.of(2026, 7, 22)
        );

        assertThat(charges.scheduleVersion()).isEqualTo("SIM-IN-DELIVERY-2026-03");
        assertThat(charges.simulated()).isTrue();
        assertThat(charges.stt()).isEqualByComparingTo("100.0000");
        assertThat(charges.exchangeCharges()).isEqualByComparingTo("3.0700");
        assertThat(charges.sebiCharges()).isEqualByComparingTo("0.1000");
        assertThat(charges.gst()).isEqualByComparingTo("0.5706");
        assertThat(charges.stampDuty()).isEqualByComparingTo("15.0000");
        assertThat(charges.totalCharges()).isEqualByComparingTo("118.7406");
        assertThat(charges.cashDebit()).isEqualByComparingTo("100118.7406");
    }

    @Test
    void sellSideDoesNotApplyStampDuty() {
        ChargeBreakdown charges = calculator.calculate(
            OrderSide.SELL,
            ProductType.DELIVERY,
            MarketExchange.NSE,
            new BigDecimal("100000.00"),
            LocalDate.of(2026, 7, 22)
        );

        assertThat(charges.stampDuty()).isEqualByComparingTo("0.0000");
        assertThat(charges.totalCharges()).isEqualByComparingTo("103.7406");
        assertThat(charges.cashCredit()).isEqualByComparingTo("99896.2594");
    }

    @Test
    void preservesHistoricScheduleForOldTrades() {
        ChargeBreakdown charges = calculator.calculate(
            OrderSide.BUY,
            ProductType.DELIVERY,
            MarketExchange.NSE,
            new BigDecimal("100000.00"),
            LocalDate.of(2025, 12, 1)
        );

        assertThat(charges.scheduleVersion()).isEqualTo("SIM-IN-DELIVERY-2024-10");
        assertThat(charges.exchangeCharges()).isEqualByComparingTo("2.9700");
    }
}
