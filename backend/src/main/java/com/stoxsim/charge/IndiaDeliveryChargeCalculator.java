package com.stoxsim.charge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.ProductType;
import com.stoxsim.order.service.TradingValidationException;

@Component
public class IndiaDeliveryChargeCalculator implements ChargeCalculator {

    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final BigDecimal STT_RATE = new BigDecimal("0.001");
    private static final BigDecimal SEBI_RATE = new BigDecimal("0.000001");
    private static final BigDecimal STAMP_DUTY_BUY_RATE = new BigDecimal("0.00015");
    private static final BigDecimal GST_RATE = new BigDecimal("0.18");

    private static final List<Schedule> SCHEDULES = List.of(
        new Schedule(
            "SIM-IN-DELIVERY-2024-10",
            LocalDate.of(2024, 10, 1),
            new BigDecimal("0.0000297")
        ),
        new Schedule(
            "SIM-IN-DELIVERY-2026-03",
            LocalDate.of(2026, 3, 1),
            new BigDecimal("0.0000307")
        )
    );

    @Override
    public ChargeBreakdown calculate(
        OrderSide side,
        ProductType product,
        MarketExchange exchange,
        BigDecimal turnover,
        LocalDate tradeDate
    ) {
        if (product != ProductType.DELIVERY || exchange != MarketExchange.NSE) {
            throw new TradingValidationException(
                "The simulated charge engine currently supports NSE delivery trades only"
            );
        }
        if (turnover == null || turnover.signum() <= 0 || tradeDate == null) {
            throw new TradingValidationException("Turnover and trade date are required for charges");
        }

        Schedule schedule = SCHEDULES.stream()
            .filter(candidate -> !candidate.effectiveFrom().isAfter(tradeDate))
            .max(Comparator.comparing(Schedule::effectiveFrom))
            .orElseThrow(() -> new TradingValidationException(
                "No simulated charge schedule is available for " + tradeDate
            ));

        BigDecimal brokerage = ZERO;
        BigDecimal stt = charge(turnover, STT_RATE);
        BigDecimal exchangeCharges = charge(turnover, schedule.exchangeTransactionRate());
        BigDecimal sebiCharges = charge(turnover, SEBI_RATE);
        BigDecimal stampDuty = side == OrderSide.BUY
            ? charge(turnover, STAMP_DUTY_BUY_RATE)
            : ZERO;
        BigDecimal dpCharges = ZERO;
        BigDecimal gst = charge(
            brokerage.add(exchangeCharges).add(sebiCharges),
            GST_RATE
        );
        BigDecimal total = brokerage
            .add(stt)
            .add(exchangeCharges)
            .add(gst)
            .add(sebiCharges)
            .add(stampDuty)
            .add(dpCharges)
            .setScale(4, RoundingMode.HALF_UP);

        return new ChargeBreakdown(
            schedule.version(),
            true,
            turnover,
            brokerage,
            stt,
            exchangeCharges,
            gst,
            sebiCharges,
            stampDuty,
            dpCharges,
            total
        );
    }

    private BigDecimal charge(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    private record Schedule(
        String version,
        LocalDate effectiveFrom,
        BigDecimal exchangeTransactionRate
    ) {
    }
}
