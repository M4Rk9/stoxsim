package com.stoxsim.charge;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.ProductType;

class UnitedStatesChargeCalculatorTest {

    private final IndiaDeliveryChargeCalculator calculator =
        new IndiaDeliveryChargeCalculator();

    @Test
    void startsUsMvpWithExplicitZeroCommissionSchedule() {
        var charges = calculator.calculate(
            OrderSide.BUY,
            ProductType.DELIVERY,
            MarketExchange.NASDAQ,
            new BigDecimal("1000.00"),
            LocalDate.of(2026, 8, 1)
        );

        assertThat(charges.scheduleVersion()).isEqualTo(
            "SIM-US-ZERO-COMMISSION-2026-08"
        );
        assertThat(charges.totalCharges()).isEqualByComparingTo("0.0000");
        assertThat(charges.cashDebit()).isEqualByComparingTo("1000.0000");
    }
}
