package com.stoxsim.charge;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.ProductType;

@RestController
@RequestMapping("/api/v1/trading/charges")
public class ChargeEstimateController {

    private final ChargeCalculator calculator;

    public ChargeEstimateController(ChargeCalculator calculator) {
        this.calculator = calculator;
    }

    @GetMapping("/estimate")
    public ChargeBreakdown estimate(
        @RequestParam OrderSide side,
        @RequestParam(defaultValue = "NSE") MarketExchange exchange,
        @RequestParam BigDecimal turnover,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate tradeDate
    ) {
        return calculator.calculate(
            side,
            ProductType.DELIVERY,
            exchange,
            turnover,
            tradeDate == null ? LocalDate.now() : tradeDate
        );
    }
}
