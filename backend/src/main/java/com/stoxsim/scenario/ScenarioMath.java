package com.stoxsim.scenario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Pure synthetic model: cumulative price shocks relative to the same starting marks. */
public final class ScenarioMath {
    private ScenarioMath() {}
    public record Step(int step, BigDecimal shockPercent, BigDecimal equity, BigDecimal change) {}
    public record Projection(List<Step> steps, BigDecimal returnPercent, BigDecimal maximumDrawdownPercent) {}
    public static BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    public static BigDecimal stressed(BigDecimal value, BigDecimal shock) {
        return money(value.multiply(BigDecimal.ONE.add(shock.movePointLeft(2))));
    }
    public static Projection project(BigDecimal cash, BigDecimal invested, List<BigDecimal> shocks) {
        if (cash.signum() < 0 || invested.signum() < 0 || shocks.isEmpty() || shocks.size() > 12
            || shocks.stream().anyMatch(s -> s == null || s.compareTo(new BigDecimal("-100")) < 0 || s.compareTo(new BigDecimal("100")) > 0))
            throw new IllegalArgumentException("Invalid synthetic inputs");
        BigDecimal initial = money(cash.add(invested)), peak = initial, drawdown = BigDecimal.ZERO;
        var steps = new ArrayList<Step>();
        steps.add(new Step(0, BigDecimal.ZERO, initial, money(BigDecimal.ZERO)));
        for (var shock : shocks) {
            var equity = money(cash.add(stressed(invested, shock)));
            peak = peak.max(equity);
            if (peak.signum() > 0) drawdown = drawdown.max(peak.subtract(equity).multiply(new BigDecimal("100")).divide(peak, 8, RoundingMode.HALF_UP));
            steps.add(new Step(steps.size(), shock, equity, money(equity.subtract(initial))));
        }
        BigDecimal change = steps.getLast().change();
        return new Projection(List.copyOf(steps), initial.signum() == 0 ? null : change.multiply(new BigDecimal("100")).divide(initial, 4, RoundingMode.HALF_UP), money(drawdown));
    }
}
