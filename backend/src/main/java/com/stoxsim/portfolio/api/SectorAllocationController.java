package com.stoxsim.portfolio.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import com.stoxsim.portfolio.service.PortfolioValuationService;
import com.stoxsim.market.service.StockInsightsService;
import com.stoxsim.instrument.domain.MarketExchange;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/portfolio/sectors")
public class SectorAllocationController {
    private final PortfolioValuationService valuation;
    private final StockInsightsService insights;
    public SectorAllocationController(PortfolioValuationService valuation, StockInsightsService insights) {
        this.valuation=valuation; this.insights=insights;
    }
    public record Sector(String name, BigDecimal value, BigDecimal percent) {}
    @GetMapping public List<Sector> sectors(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        var portfolio = valuation.valueForAccount(UUID.fromString(jwt.getSubject()), accountId);
        Map<String, BigDecimal> values = new TreeMap<>();
        for (var holding : portfolio.holdings()) {
            if (holding.marketValue().signum() <= 0) continue;
            String sector = "Unclassified";
            try {
                var profile = insights.get(portfolio.marketRegion(), MarketExchange.valueOf(holding.exchange()), holding.symbol(), "quarterly").profile();
                if (profile != null && profile.sector() != null && !profile.sector().isBlank()) sector = profile.sector().trim();
            } catch (RuntimeException ignored) { /* Missing classifications stay visible. */ }
            values.merge(sector, holding.marketValue(), BigDecimal::add);
        }
        var total = values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) return List.of();
        return values.entrySet().stream().map(e -> new Sector(e.getKey(), e.getValue(), e.getValue()
            .multiply(new BigDecimal("100")).divide(total, 4, RoundingMode.HALF_UP)))
            .sorted(Comparator.comparing(Sector::value).reversed()).toList();
    }
}
