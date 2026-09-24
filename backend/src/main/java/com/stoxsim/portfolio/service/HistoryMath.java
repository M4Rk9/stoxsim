package com.stoxsim.portfolio.service;

import java.util.List;

/** Sample statistics on aligned session returns, never on currency amounts. */
public final class HistoryMath {
    private HistoryMath() {}
    public record Risk(Double volatilityPercent, Double beta, Double correlation) {}
    public static Risk risk(List<Double> returns, List<Double> benchmark) {
        if (returns.size()<20) return new Risk(null,null,null);
        double mean=returns.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance=returns.stream().mapToDouble(x->(x-mean)*(x-mean)).sum()/(returns.size()-1);
        double volatility=Math.sqrt(variance*252)*100;
        if (benchmark.size()!=returns.size()) return new Risk(volatility,null,null);
        double bm=benchmark.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double bv=0,cov=0;
        for(int i=0;i<returns.size();i++) {
            double b=benchmark.get(i)-bm;
            bv+=b*b;cov+=(returns.get(i)-mean)*b;
        }
        bv/=returns.size()-1;cov/=returns.size()-1;
        return new Risk(volatility,bv<=1e-20?null:cov/bv,
            bv<=1e-20||variance<=1e-20?null:Math.max(-1,Math.min(1,cov/Math.sqrt(variance*bv))));
    }
}
