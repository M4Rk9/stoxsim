package com.stoxsim.portfolio.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.service.TradingQueryService;

@RestController
@RequestMapping("/api/v1/holdings")
public class PortfolioController {

    private final TradingQueryService queries;

    public PortfolioController(TradingQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<HoldingResponse> holdings(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "INDIA") MarketRegion marketRegion
    ) {
        return queries.holdings(UUID.fromString(jwt.getSubject()), marketRegion);
    }
}
