package com.stoxsim.scenario;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioMathTest {
    @Test void cashCushionsCumulativeShocksAndRecoveryKeepsPathDrawdown() {
        var result = ScenarioMath.project(new BigDecimal("200"), new BigDecimal("800"), List.of(new BigDecimal("-50"), BigDecimal.ZERO, new BigDecimal("25")));
        assertThat(result.steps()).extracting(s -> s.equity().toPlainString()).containsExactly("1000.0000", "600.0000", "1000.0000", "1200.0000");
        assertThat(result.returnPercent()).isEqualByComparingTo("20");
        assertThat(result.maximumDrawdownPercent()).isEqualByComparingTo("40");
        assertThat(ScenarioMath.project(new BigDecimal("200"), new BigDecimal("800"), List.of(new BigDecimal("-50"), BigDecimal.ZERO, new BigDecimal("25")))).isEqualTo(result);
    }
    @Test void peakDrawdownAndTotalLossUseStartingCash() {
        var result = ScenarioMath.project(BigDecimal.ZERO, new BigDecimal("100"), List.of(new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("-100")));
        assertThat(result.steps().get(2).equity()).isEqualByComparingTo("100");
        assertThat(result.maximumDrawdownPercent()).isEqualByComparingTo("100");
        assertThat(result.returnPercent()).isEqualByComparingTo("-100");
    }
    @Test void emptyAndCashOnlyPortfoliosDoNotInventReturns() {
        var empty=ScenarioMath.project(BigDecimal.ZERO, BigDecimal.ZERO, List.of(new BigDecimal("-100")));
        assertThat(empty.returnPercent()).isNull();
        assertThat(empty.maximumDrawdownPercent()).isEqualByComparingTo("0");
        var cash=ScenarioMath.project(new BigDecimal("123.4567"), BigDecimal.ZERO, List.of(new BigDecimal("-100"), new BigDecimal("100")));
        assertThat(cash.steps()).allMatch(s -> s.equity().compareTo(new BigDecimal("123.4567")) == 0);
    }
    @Test void invalidInputsAreRejected() {
        assertThatThrownBy(() -> ScenarioMath.project(BigDecimal.ZERO, BigDecimal.ONE, List.of(new BigDecimal("-101")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScenarioMath.project(BigDecimal.ZERO, BigDecimal.ONE, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScenarioMath.project(new BigDecimal("-1"), BigDecimal.ONE, List.of(BigDecimal.ZERO))).isInstanceOf(IllegalArgumentException.class);
    }
}
