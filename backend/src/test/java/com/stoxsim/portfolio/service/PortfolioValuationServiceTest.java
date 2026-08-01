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
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.data.MarketDataStatus;
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
    void valuesAnEmptyIndiaPortfolioFromCash() {
        UUID userId = UUID.randomUUID();
        var account = account(MarketRegion.INDIA, "500000.00");
        when(accounts.findByUserIdAndMarketRegion(userId, MarketRegion.INDIA))
            .thenReturn(Optional.of(account));
        when(holdings.findAllByAccountUserIdAndAccountMarketRegionAndQuantityGreaterThanOrderByInstrumentTradingSymbol(
            userId,
            MarketRegion.INDIA,
            0
        )).thenReturn(List.of());
        when(properties.getIndiaStartingBalance()).thenReturn(new BigDecimal("500000.00"));
        when(marketData.marketStatus(MarketRegion.INDIA, MarketExchange.NSE))
            .thenReturn(MarketDataStatus.CLOSED);

        var response = service().value(userId, MarketRegion.INDIA);

        assertThat(response.totalAccountValue()).isEqualByComparingTo("500000.0000");
        assertThat(response.totalProfitLoss()).isEqualByComparingTo("0.0000");
        assertThat(response.holdings()).isEmpty();
    }

    @Test
    void valuesAnEmptyUnitedStatesPortfolioUsingNasdaqStatus() {
        UUID userId = UUID.randomUUID();
        var account = account(MarketRegion.UNITED_STATES, "10000.00");
        when(accounts.findByUserIdAndMarketRegion(
            userId,
            MarketRegion.UNITED_STATES
        )).thenReturn(Optional.of(account));
        when(holdings.findAllByAccountUserIdAndAccountMarketRegionAndQuantityGreaterThanOrderByInstrumentTradingSymbol(
            userId,
            MarketRegion.UNITED_STATES,
            0
        )).thenReturn(List.of());
        when(properties.getUnitedStatesStartingBalance())
            .thenReturn(new BigDecimal("10000.00"));
        when(marketData.marketStatus(
            MarketRegion.UNITED_STATES,
            MarketExchange.NASDAQ
        )).thenReturn(MarketDataStatus.CLOSED);

        var response = service().value(userId, MarketRegion.UNITED_STATES);

        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.totalAccountValue()).isEqualByComparingTo("10000.0000");
        assertThat(response.totalProfitLoss()).isEqualByComparingTo("0.0000");
        assertThat(response.holdings()).isEmpty();
    }

    private PortfolioValuationService service() {
        return new PortfolioValuationService(
            accounts,
            holdings,
            marketData,
            properties
        );
    }

    private VirtualAccount account(MarketRegion region, String balance) {
        return new VirtualAccount(
            new AppUser("learner@example.com", "hash", "Learner"),
            region,
            new BigDecimal(balance)
        );
    }
}
