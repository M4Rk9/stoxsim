package com.stoxsim.account.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.account.service.AccountService;
import com.stoxsim.auth.api.dto.AccountResponse;
import com.stoxsim.subscription.service.SubscriptionService;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accounts;
    private final SubscriptionService subscriptions;

    public AccountController(
        AccountService accounts,
        SubscriptionService subscriptions
    ) {
        this.accounts = accounts;
        this.subscriptions = subscriptions;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accounts.listOwned(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/sandboxes")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAdditionalSandbox(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return subscriptions.createAdditionalSandbox(
            UUID.fromString(jwt.getSubject()),
            idempotencyKey
        );
    }
}
