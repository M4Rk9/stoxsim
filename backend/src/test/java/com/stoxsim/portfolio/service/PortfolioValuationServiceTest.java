package com.stoxsim.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stoxsim.account.config.AccountProperties;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.service.MarketDataService;
import com.stoxsim.portfolio.repository.HoldingRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioValuationServiceTest {

    @Mock private VirtualAccountRepository accounts;
    @Mock private HoldingRepository holdings;
    @Mock private MarketDataService marketData;
    @Mock private AccountProperties properties;

    @Test
    void valuesAnEmptyPortfolioFromCash() {
        UUID userId = UUID.randomUUID();
        var account = new VirtualAccount(
            new AppUser("learner@example.com", "hash", "Learner"),
            MarketRegion.INDIA,
            new BigDecimal("500000.00")
        );
        when(accounts.findByUserIdAndMarketRegion(userId, MarketRegion.INDIA))
            .thenReturn(Optional.of(account));
        when(holdings.findAllByAccountUserIdAndAccountMarketRegionAndQuantityGreaterThanOrderByInstrumentTradingSymbol(
            userId,
            MarketRegion.INDIA,
            0
        )).thenReturn(List.of());
        when(properties.getIndiaStartingBalance()).thenReturn(new BigDecimal("500000.00"));

        var service = new PortfolioValuationService(accounts, holdings, marketData, properties);
        var response = service.value(userId, MarketRegion.INDIA);

        assertThat(response.totalAccountValue()).isEqualByComparingTo("500000.0000");
        assertThat(response.totalProfitLoss()).isEqualByComparingTo("0.0000");
        assertThat(response.holdings()).isEmpty();
    }
}
