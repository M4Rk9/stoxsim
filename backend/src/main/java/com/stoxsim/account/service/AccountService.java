package com.stoxsim.account.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.account.config.AccountProperties;
import com.stoxsim.account.domain.AccountKind;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.api.dto.AccountResponse;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.market.domain.MarketRegion;

@Service
public class AccountService {

    private final VirtualAccountRepository accountRepository;
    private final AccountProperties properties;

    public AccountService(VirtualAccountRepository accountRepository, AccountProperties properties) {
        this.accountRepository = accountRepository;
        this.properties = properties;
    }

    @Transactional
    public List<VirtualAccount> createDefaultAccounts(AppUser user) {
        var accounts = List.of(
            new VirtualAccount(user, MarketRegion.INDIA, properties.getIndiaStartingBalance()),
            new VirtualAccount(user, MarketRegion.UNITED_STATES, properties.getUnitedStatesStartingBalance())
        );
        return accountRepository.saveAll(accounts);
    }

    @Transactional(readOnly = true)
    public List<VirtualAccount> findByUserId(UUID userId) {
        return accountRepository.findAllByUserIdOrderByMarketRegion(userId);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listOwned(UUID userId) {
        return accountRepository.findAllOwnedByUserId(userId)
            .stream()
            .sorted(Comparator
                .comparingInt(AccountService::scopeOrder)
                .thenComparing(VirtualAccount::getMarketRegion)
                .thenComparingInt(VirtualAccount::getSandboxSlot))
            .map(AccountResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public VirtualAccount requireOwned(UUID userId, UUID accountId) {
        return accountRepository.findOwnedById(userId, accountId)
            .orElseThrow(AccountService::accountNotFound);
    }

    public static void requireTradingEnabled(VirtualAccount account) {
        if (account.isActive()) {
            return;
        }
        if (account.getAccountKind() == AccountKind.SANDBOX) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This sandbox is locked because its paid entitlement is inactive"
            );
        }
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "This portfolio is inactive"
        );
    }

    private static ResponseStatusException accountNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }

    private static int scopeOrder(VirtualAccount account) {
        return account.getAccountKind() == AccountKind.STANDARD ? 0 : 1;
    }
}
