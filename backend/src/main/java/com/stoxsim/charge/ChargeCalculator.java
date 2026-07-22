package com.stoxsim.charge;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.ProductType;

public interface ChargeCalculator {

    ChargeBreakdown calculate(
        OrderSide side,
        ProductType product,
        MarketExchange exchange,
        BigDecimal turnover,
        LocalDate tradeDate
    );
}
