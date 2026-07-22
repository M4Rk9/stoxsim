package com.stoxsim.market.provider.upstox;

import com.stoxsim.market.data.CandleInterval;

record UpstoxCandleInterval(String unit, int interval) {

    static UpstoxCandleInterval from(CandleInterval interval) {
        return switch (interval) {
            case ONE_MINUTE -> new UpstoxCandleInterval("minutes", 1);
            case THREE_MINUTES -> new UpstoxCandleInterval("minutes", 3);
            case FIFTEEN_MINUTES -> new UpstoxCandleInterval("minutes", 15);
            case ONE_HOUR -> new UpstoxCandleInterval("hours", 1);
            case ONE_DAY -> new UpstoxCandleInterval("days", 1);
            case ONE_WEEK -> new UpstoxCandleInterval("weeks", 1);
            case ONE_MONTH -> new UpstoxCandleInterval("months", 1);
        };
    }
}
