package com.stoxsim.account.api;

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
@RequestMapping("/api/v1/account/ledger")
public class LedgerController {

    private final TradingQueryService queries;

    public LedgerController(TradingQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<LedgerEntryResponse> ledger(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "INDIA") MarketRegion marketRegion
    ) {
        return queries.ledger(UUID.fromString(jwt.getSubject()), marketRegion);
    }
}
