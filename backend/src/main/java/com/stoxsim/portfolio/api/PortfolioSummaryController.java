package com.stoxsim.portfolio.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.service.PortfolioAnalyticsService;
import com.stoxsim.portfolio.service.PortfolioInsightsService;
import com.stoxsim.portfolio.service.PortfolioValuationService;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioSummaryController {

    private final PortfolioValuationService valuation;
    private final PortfolioAnalyticsService analytics;
    private final PortfolioInsightsService insights;

    public PortfolioSummaryController(
        PortfolioValuationService valuation,
        PortfolioAnalyticsService analytics,
        PortfolioInsightsService insights
    ) {
        this.valuation = valuation;
        this.analytics = analytics;
        this.insights = insights;
    }

    @GetMapping
    public PortfolioResponse portfolio(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "INDIA") MarketRegion marketRegion
    ) {
        return valuation.value(UUID.fromString(jwt.getSubject()), marketRegion);
    }

    @GetMapping("/analytics")
    public PortfolioAnalyticsResponse analytics(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "INDIA") MarketRegion marketRegion
    ) {
        return analytics.analyze(UUID.fromString(jwt.getSubject()), marketRegion);
    }

    @GetMapping("/insights")
    public PortfolioInsightsResponse insights(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "INDIA") MarketRegion marketRegion
    ) {
        return insights.analyze(UUID.fromString(jwt.getSubject()), marketRegion);
    }
}
