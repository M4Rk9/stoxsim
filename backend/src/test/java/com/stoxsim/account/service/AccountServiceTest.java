package com.stoxsim.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stoxsim.account.config.AccountProperties;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.market.domain.MarketRegion;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private VirtualAccountRepository repository;

    @Mock
    private AccountProperties properties;

    @Test
    void createsIndependentIndiaAndUnitedStatesAccounts() {
        when(properties.getIndiaStartingBalance()).thenReturn(new BigDecimal("500000.00"));
        when(properties.getUnitedStatesStartingBalance()).thenReturn(new BigDecimal("10000.00"));
        when(repository.saveAll(org.mockito.ArgumentMatchers.<List<VirtualAccount>>any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var service = new AccountService(repository, properties);
        var user = new AppUser("marky@example.com", "hash", "Marky");

        List<VirtualAccount> accounts = service.createDefaultAccounts(user);

        assertThat(accounts)
            .extracting(VirtualAccount::getMarketRegion)
            .containsExactly(MarketRegion.INDIA, MarketRegion.UNITED_STATES);
        assertThat(accounts)
            .extracting(VirtualAccount::getCurrency)
            .containsExactly("INR", "USD");
        assertThat(accounts)
            .extracting(VirtualAccount::getAvailableCash)
            .containsExactly(new BigDecimal("500000.00"), new BigDecimal("10000.00"));

        verify(repository).saveAll(accounts);
    }
}
