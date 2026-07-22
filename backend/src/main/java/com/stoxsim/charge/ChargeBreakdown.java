package com.stoxsim.charge;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ChargeBreakdown(
    String scheduleVersion,
    boolean simulated,
    BigDecimal turnover,
    BigDecimal brokerage,
    BigDecimal stt,
    BigDecimal exchangeCharges,
    BigDecimal gst,
    BigDecimal sebiCharges,
    BigDecimal stampDuty,
    BigDecimal dpCharges,
    BigDecimal totalCharges
) {
    private static final int MONEY_SCALE = 4;

    public ChargeBreakdown {
        turnover = money(turnover);
        brokerage = money(brokerage);
        stt = money(stt);
        exchangeCharges = money(exchangeCharges);
        gst = money(gst);
        sebiCharges = money(sebiCharges);
        stampDuty = money(stampDuty);
        dpCharges = money(dpCharges);
        totalCharges = money(totalCharges);
    }

    public BigDecimal cashDebit() {
        return money(turnover.add(totalCharges));
    }

    public BigDecimal cashCredit() {
        return money(turnover.subtract(totalCharges));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
