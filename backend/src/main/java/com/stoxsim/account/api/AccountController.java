package com.stoxsim.account.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.account.service.AccountService;
import com.stoxsim.auth.api.dto.AccountResponse;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accounts.listOwned(UUID.fromString(jwt.getSubject()));
    }
}
