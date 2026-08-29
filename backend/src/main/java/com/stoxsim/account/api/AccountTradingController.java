package com.stoxsim.account.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.order.api.ModifyOrderRequest;
import com.stoxsim.order.api.OrderResponse;
import com.stoxsim.order.api.PlaceOrderRequest;
import com.stoxsim.order.service.OrderApplicationService;
import com.stoxsim.order.service.TradingQueryService;
import com.stoxsim.portfolio.api.HoldingResponse;
import com.stoxsim.portfolio.api.PortfolioAnalyticsResponse;
import com.stoxsim.portfolio.api.PortfolioInsightsResponse;
import com.stoxsim.portfolio.api.PortfolioResponse;
import com.stoxsim.portfolio.service.PortfolioAnalyticsService;
import com.stoxsim.portfolio.service.PortfolioInsightsService;
import com.stoxsim.portfolio.service.PortfolioValuationService;
import com.stoxsim.trade.api.TradeResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
public class AccountTradingController {

    private final OrderApplicationService orders;
    private final TradingQueryService queries;
    private final PortfolioValuationService valuation;
    private final PortfolioAnalyticsService analytics;
    private final PortfolioInsightsService insights;

    public AccountTradingController(
        OrderApplicationService orders,
        TradingQueryService queries,
        PortfolioValuationService valuation,
        PortfolioAnalyticsService analytics,
        PortfolioInsightsService insights
    ) {
        this.orders = orders;
        this.queries = queries;
        this.valuation = valuation;
        this.analytics = analytics;
        this.insights = insights;
    }

    @GetMapping("/portfolio")
    public PortfolioResponse portfolio(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID accountId
    ) {
        return valuation.valueForAccount(userId(jwt), accountId);
    }

    @GetMapping("/portfolio/analytics")
    public PortfolioAnalyticsResponse analytics(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID accountId
    ) {
        return analytics.analyzeForAccount(userId(jwt), accountId);
    }

    @GetMapping("/portfolio/insights")
    public PortfolioInsightsResponse insights(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID accountId
    ) {
        return insights.analyzeForAccount(userId(jwt), accountId);
    }

    @GetMapping("/holdings")
    public List<HoldingResponse> holdings(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID accountId
    ) {
        return queries.holdingsForAccount(userId(jwt), accountId);
    }

    @GetMapping("/trades")
    public List<TradeResponse> trades(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID accountId
    ) {
        return queries.tradesForAccount(userId(jwt), accountId);
    }

    @GetMapping("/ledger")
    public List<LedgerEntryResponse> ledger(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID accountId
    ) {
        return queries.ledgerForAccount(userId(jwt), accountId);
    }

    @GetMapping("/orders")
    public List<OrderResponse> orders(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID accountId
    ) {
        return orders.listForAccount(userId(jwt), accountId);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID accountId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PlaceOrderRequest request
    ) {
        return orders.placeForAccount(
            userId(jwt),
            accountId,
            idempotencyKey,
            request
        );
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse order(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID accountId,
        @PathVariable UUID orderId
    ) {
        return orders.getForAccount(userId(jwt), accountId, orderId);
    }

    @PutMapping("/orders/{orderId}")
    public OrderResponse modify(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID accountId,
        @PathVariable UUID orderId,
        @Valid @RequestBody ModifyOrderRequest request
    ) {
        return orders.modifyForAccount(
            userId(jwt),
            accountId,
            orderId,
            request
        );
    }

    @DeleteMapping("/orders/{orderId}")
    public OrderResponse cancel(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID accountId,
        @PathVariable UUID orderId
    ) {
        return orders.cancelForAccount(userId(jwt), accountId, orderId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
