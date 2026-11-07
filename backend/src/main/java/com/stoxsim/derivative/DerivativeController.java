package com.stoxsim.derivative;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.derivative.DerivativeBook.Action;

@RestController
@RequestMapping("/api/v1/derivatives")
class DerivativeController {
    private final DerivativeCatalog catalog;
    private final DerivativeTradingService trading;

    DerivativeController(DerivativeCatalog catalog, DerivativeTradingService trading) {
        this.catalog = catalog;
        this.trading = trading;
    }

    @GetMapping("/capability")
    Map<String, Boolean> capability() { return Map.of("enabled", catalog.enabled()); }

    @GetMapping("/expiries")
    List<LocalDate> expiries(@RequestParam String underlying) {
        requireUnderlying(underlying);
        return catalog.expiries(underlying, trading.today());
    }

    @GetMapping("/chain")
    List<DerivativeTradingService.ContractQuote> chain(@RequestParam String underlying, @RequestParam LocalDate expiry,
        @RequestParam(defaultValue = "0") int offset) {
        requireUnderlying(underlying);
        if (offset < 0 || offset > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid strike offset.");
        return trading.chain(underlying, expiry, offset);
    }

    @GetMapping("/portfolio")
    DerivativeTradingService.Portfolio portfolio(@AuthenticationPrincipal Jwt jwt) {
        requireEnabled();
        return trading.portfolio(UUID.fromString(jwt.getSubject()));
    }

    record OrderRequest(@NotNull UUID contractId, @NotNull UUID requestKey,
        @NotNull Action action, @Min(1) @Max(100) int lots) {}

    @PostMapping("/orders")
    DerivativeTradingService.Fill place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrderRequest order) {
        requireEnabled();
        return trading.place(UUID.fromString(jwt.getSubject()), order.contractId(), order.requestKey(), order.action(), order.lots());
    }

    private void requireUnderlying(String key) {
        requireEnabled();
        if (!DerivativeCatalog.UNDERLYINGS.contains(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported index.");
    }

    private void requireEnabled() {
        if (!catalog.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
