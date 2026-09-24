package com.stoxsim.portfolio.service;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class HistoryMathTest {
    @Test void alignedSampleHasKnownBetaCorrelationAndVolatility() {
        var benchmark=IntStream.range(0,20).mapToObj(i->i%2==0?0.01:-0.01).toList();
        var portfolio=benchmark.stream().map(x->2*x).toList();
        var risk=HistoryMath.risk(portfolio,benchmark);
        assertThat(risk.beta()).isCloseTo(2.0,within(1e-12));
        assertThat(risk.correlation()).isCloseTo(1.0,within(1e-12));
        assertThat(risk.volatilityPercent()).isCloseTo(Math.sqrt(20*0.0004/19*252)*100,within(1e-10));
    }
    @Test void limitedAndConstantSeriesNeverInventRiskRatios() {
        assertThat(HistoryMath.risk(List.of(0.1),List.of(0.2)).volatilityPercent()).isNull();
        var zero=java.util.Collections.nCopies(20,0.0);
        var risk=HistoryMath.risk(zero,zero);
        assertThat(risk.volatilityPercent()).isZero();assertThat(risk.beta()).isNull();assertThat(risk.correlation()).isNull();
        assertThat(HistoryMath.risk(zero,List.of()).beta()).isNull();
    }
}
