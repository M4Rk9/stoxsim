package com.stoxsim.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.account.config.AccountProperties;
import com.stoxsim.account.domain.AccountKind;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.subscription.domain.SubscriptionPlan;

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
        assertThat(accounts)
            .allSatisfy(account -> {
                assertThat(account.getAccountKind()).isEqualTo(AccountKind.STANDARD);
                assertThat(account.isLeaderboardEligible()).isTrue();
                assertThat(account.getStartingCapital())
                    .isEqualByComparingTo(account.getAvailableCash());
            });

        verify(repository).saveAll(accounts);
    }

    @Test
    void resolvesOnlyAnOwnedAccount() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        VirtualAccount account = new VirtualAccount(
            new AppUser("owner@example.com", "hash", "Owner"),
            MarketRegion.INDIA,
            new BigDecimal("500000.00")
        );
        when(repository.findOwnedById(userId, accountId))
            .thenReturn(Optional.of(account));

        VirtualAccount resolved = new AccountService(repository, properties)
            .requireOwned(userId, accountId);

        assertThat(resolved).isSameAs(account);
    }

    @Test
    void rejectsTradingThroughALockedSandbox() {
        VirtualAccount sandbox = VirtualAccount.sandbox(
            new AppUser("sandbox@example.com", "hash", "Sandbox"),
            SubscriptionPlan.PLUS,
            1,
            SubscriptionPlan.PLUS.sandboxCapitalInr()
        );
        sandbox.deactivate();

        assertThatThrownBy(() -> AccountService.requireTradingEnabled(sandbox))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("paid entitlement is inactive");
    }
}
