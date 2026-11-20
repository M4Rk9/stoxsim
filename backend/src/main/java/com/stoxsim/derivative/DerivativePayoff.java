package com.stoxsim.derivative;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import com.stoxsim.derivative.DerivativeBook.Action;
import com.stoxsim.derivative.DerivativeCatalog.Kind;
import com.stoxsim.derivative.DerivativeTradingService.Preview;

/** Piecewise-linear expiry P&L for new, same-underlying/same-expiry opening legs. */
final class DerivativePayoff {
    public record Point(BigDecimal underlying, BigDecimal pnl) {}
    public record ZeroRange(BigDecimal from, BigDecimal to) {}
    public record Result(List<Point> points, List<BigDecimal> breakevens, List<ZeroRange> zeroRanges,
        BigDecimal maximumLoss, boolean unlimitedLoss, BigDecimal maximumProfit, boolean unlimitedProfit) {}

    static BigDecimal at(List<Preview> legs, BigDecimal spot) {
        BigDecimal total = BigDecimal.ZERO;
        for (Preview leg : legs) {
            BigDecimal value = DerivativeBook.expiryPrice(leg.contract(), spot).subtract(leg.price());
            if (leg.action() == Action.OPEN_SHORT) value = value.negate();
            total = total.add(value.multiply(BigDecimal.valueOf(leg.quantity())));
        }
        return total;
    }

    static Result calculate(List<Preview> legs) {
        var knots = new TreeSet<BigDecimal>();
        knots.add(BigDecimal.ZERO);
        BigDecimal scale = BigDecimal.ONE;
        BigDecimal tail = BigDecimal.ZERO;
        for (Preview leg : legs) {
            if (leg.contract().strike() != null) {
                knots.add(leg.contract().strike());
                scale = scale.max(leg.contract().strike());
            } else scale = scale.max(leg.price());
            if (leg.contract().kind() != Kind.PE) {
                BigDecimal q = BigDecimal.valueOf(leg.quantity());
                tail = tail.add(leg.action() == Action.OPEN_SHORT ? q.negate() : q);
            }
        }
        var roots = new TreeSet<BigDecimal>();
        var ranges = new ArrayList<ZeroRange>();
        var ordered = new ArrayList<>(knots);
        BigDecimal min = at(legs, BigDecimal.ZERO), max = min;
        for (int i = 0; i < ordered.size(); i++) {
            BigDecimal x = ordered.get(i), y = at(legs, x);
            min = min.min(y); max = max.max(y);
            if (y.signum() == 0) roots.add(x);
            if (i + 1 < ordered.size()) {
                BigDecimal next = ordered.get(i + 1), nextY = at(legs, next);
                if (y.signum() == 0 && nextY.signum() == 0) ranges.add(new ZeroRange(x, next));
                else if (y.signum() * nextY.signum() < 0)
                    roots.add(x.add(y.negate().multiply(next.subtract(x)).divide(nextY.subtract(y), 8, RoundingMode.HALF_UP)));
            }
        }
        BigDecimal last = ordered.getLast(), lastY = at(legs, last);
        if (tail.signum() == 0 && lastY.signum() == 0) ranges.add(new ZeroRange(last, null));
        else if (tail.signum() * lastY.signum() < 0)
            roots.add(last.add(lastY.negate().divide(tail, 8, RoundingMode.HALF_UP)));
        BigDecimal upper = scale.multiply(new BigDecimal("1.25"));
        if (!roots.isEmpty()) upper = upper.max(roots.last().multiply(new BigDecimal("1.10")));
        knots.add(upper);
        knots.addAll(roots);
        List<Point> points = knots.stream().map(x -> new Point(x, at(legs, x))).toList();
        return new Result(points, List.copyOf(roots), ranges,
            tail.signum() < 0 ? null : min.negate().max(BigDecimal.ZERO), tail.signum() < 0,
            tail.signum() > 0 ? null : max.max(BigDecimal.ZERO), tail.signum() > 0);
    }
}
