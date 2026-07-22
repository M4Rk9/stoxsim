package com.stoxsim.account.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stoxsim.account.config.AccountProperties;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
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
}
