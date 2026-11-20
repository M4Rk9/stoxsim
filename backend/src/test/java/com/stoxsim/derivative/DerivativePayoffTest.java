package com.stoxsim.derivative;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.stoxsim.derivative.DerivativeBook.Action;
import com.stoxsim.derivative.DerivativeCatalog.Contract;
import com.stoxsim.derivative.DerivativeCatalog.Kind;
import com.stoxsim.derivative.DerivativeTradingService.Preview;

class DerivativePayoffTest {
    private Preview leg(Kind kind, Action action, String strike, String premium) {
        var c = new Contract(UUID.randomUUID(), "NSE_FO|1", "NSE_INDEX|Nifty 50", "Synthetic fixture",
            kind, LocalDate.of(2026, 12, 29), 10, new BigDecimal("0.05"), strike == null ? null : new BigDecimal(strike));
        return new Preview(c, action, 1, 10, new BigDecimal(premium), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, null, null, false, Instant.EPOCH, Instant.EPOCH);
    }

    @Test void straddleHasTwoBreakevensAndPremiumBoundedLoss() {
        var legs = List.of(leg(Kind.CE, Action.OPEN_LONG, "100", "10"), leg(Kind.PE, Action.OPEN_LONG, "100", "8"));
        var result = DerivativePayoff.calculate(legs);
        assertThat(result.maximumLoss()).isEqualByComparingTo("180");
        assertThat(result.breakevens()).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("82"), new BigDecimal("118"));
        assertThat(result.unlimitedProfit()).isTrue();
        assertThat(DerivativePayoff.at(legs, new BigDecimal("100"))).isEqualByComparingTo("-180");
        assertThat(DerivativePayoff.at(legs, new BigDecimal("82"))).isEqualByComparingTo("0");
    }

    @Test void callCapsShortFutureLossBeyondTheChartRange() {
        var result = DerivativePayoff.calculate(List.of(
            leg(Kind.FUT, Action.OPEN_SHORT, null, "100"), leg(Kind.CE, Action.OPEN_LONG, "100", "5")));
        assertThat(result.maximumLoss()).isEqualByComparingTo("50");
        assertThat(result.unlimitedLoss()).isFalse();
        assertThat(result.maximumProfit()).isEqualByComparingTo("950");
        assertThat(result.breakevens().getFirst()).isEqualByComparingTo("95");
    }

    @Test void putDoesNotCapShortFutureUpsideLoss() {
        var result = DerivativePayoff.calculate(List.of(
            leg(Kind.FUT, Action.OPEN_SHORT, null, "100"), leg(Kind.PE, Action.OPEN_LONG, "90", "5")));
        assertThat(result.unlimitedLoss()).isTrue();
        assertThat(result.maximumLoss()).isNull();
        assertThat(result.maximumProfit()).isEqualByComparingTo("1850");
    }

    @Test void protectivePutCapsLongFutureLossAndTailRootIsIncluded() {
        var result = DerivativePayoff.calculate(List.of(
            leg(Kind.FUT, Action.OPEN_LONG, null, "100"), leg(Kind.PE, Action.OPEN_LONG, "100", "5")));
        assertThat(result.maximumLoss()).isEqualByComparingTo("50");
        assertThat(result.breakevens().getFirst()).isEqualByComparingTo("105");
        assertThat(result.unlimitedProfit()).isTrue();
    }

    @Test void reportsFlatZeroTailRatherThanInventingSingleBreakeven() {
        var result = DerivativePayoff.calculate(List.of(
            leg(Kind.FUT, Action.OPEN_SHORT, null, "105"), leg(Kind.CE, Action.OPEN_LONG, "100", "5")));
        assertThat(result.zeroRanges()).hasSize(1);
        assertThat(result.zeroRanges().getFirst().from()).isEqualByComparingTo("100");
        assertThat(result.zeroRanges().getFirst().to()).isNull();
        assertThat(result.maximumLoss()).isEqualByComparingTo("0");
    }
}
