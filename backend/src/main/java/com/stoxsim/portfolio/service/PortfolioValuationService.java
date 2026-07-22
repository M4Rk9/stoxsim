package com.stoxsim.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.account.config.AccountProperties;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.service.MarketDataService;
import com.stoxsim.portfolio.api.PortfolioPositionResponse;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.api.PortfolioResponse;
import com.stoxsim.portfolio.domain.Holding;
import com.stoxsim.portfolio.repository.HoldingRepository;

@Service
public class PortfolioValuationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final VirtualAccountRepository accounts;
    private final HoldingRepository holdings;
    private final MarketDataService marketData;
    private final AccountProperties accountProperties;

    public PortfolioValuationService(
        VirtualAccountRepository accounts,
        HoldingRepository holdings,
        MarketDataService marketData,
        AccountProperties accountProperties
    ) {
        this.accounts = accounts;
        this.holdings = holdings;
        this.marketData = marketData;
        this.accountProperties = accountProperties;
    }

    @Transactional(readOnly = true)
    public PortfolioResponse value(UUID userId, MarketRegion marketRegion) {
        VirtualAccount account = accounts.findByUserIdAndMarketRegion(userId, marketRegion)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        List<Holding> owned = holdings
            .findAllByAccountUserIdAndAccountMarketRegionAndQuantityGreaterThanOrderByInstrumentTradingSymbol(
                userId,
                marketRegion,
                0
            );

        List<PortfolioPositionResponse> positions = new ArrayList<>();
        BigDecimal invested = money(BigDecimal.ZERO);
        BigDecimal marketValue = money(BigDecimal.ZERO);
        BigDecimal unrealized = money(BigDecimal.ZERO);
        PricingStatus overallStatus = PricingStatus.LIVE;

        for (Holding holding : owned) {
            PortfolioPositionResponse position = position(holding);
            positions.add(position);
            invested = invested.add(position.investedValue());
            marketValue = marketValue.add(position.marketValue());
            unrealized = unrealized.add(position.unrealizedProfitLoss());
            overallStatus = combine(overallStatus, position.pricingStatus());
        }

        BigDecimal startingCapital = startingCapital(marketRegion);
        BigDecimal accountValue = account.getAvailableCash()
            .add(account.getBlockedCash())
            .add(marketValue);
        BigDecimal totalProfitLoss = accountValue.subtract(startingCapital);

        return new PortfolioResponse(
            marketRegion,
            account.getCurrency(),
            money(startingCapital),
            money(account.getAvailableCash()),
            money(account.getBlockedCash()),
            money(invested),
            money(marketValue),
            money(account.getRealizedProfitLoss()),
            money(unrealized),
            money(totalProfitLoss),
            money(accountValue),
            percent(totalProfitLoss, startingCapital),
            overallStatus,
            Instant.now(),
            List.copyOf(positions)
        );
    }

    private PortfolioPositionResponse position(Holding holding) {
        BigDecimal invested = holding.getAveragePrice()
            .multiply(BigDecimal.valueOf(holding.getQuantity()));
        BigDecimal currentPrice;
        PricingStatus status;
        Instant priceTimestamp;

        try {
            Quote quote = marketData.latestQuote(holding.getInstrument());
            if (quote.lastPrice() == null || quote.lastPrice().signum() <= 0) {
                throw new IllegalStateException("Quote has no last price");
            }
            currentPrice = quote.lastPrice();
            status = marketData.isStale(quote) ? PricingStatus.STALE : PricingStatus.LIVE;
            priceTimestamp = quote.exchangeTimestamp() == null
                ? quote.receivedAt()
                : quote.exchangeTimestamp();
        } catch (RuntimeException exception) {
            currentPrice = holding.getAveragePrice();
            status = PricingStatus.UNAVAILABLE;
            priceTimestamp = null;
        }

        BigDecimal currentValue = currentPrice.multiply(BigDecimal.valueOf(holding.getQuantity()));
        BigDecimal profitLoss = currentValue.subtract(invested);
        return new PortfolioPositionResponse(
            holding.getId(),
            holding.getInstrument().getExchange().name(),
            holding.getInstrument().getTradingSymbol(),
            holding.getInstrument().getName(),
            holding.getInstrument().getCurrency(),
            holding.getQuantity(),
            holding.getBlockedQuantity(),
            holding.availableQuantity(),
            money(holding.getAveragePrice()),
            money(currentPrice),
            money(invested),
            money(currentValue),
            money(profitLoss),
            percent(profitLoss, invested),
            status,
            priceTimestamp
        );
    }

    private BigDecimal startingCapital(MarketRegion marketRegion) {
        return marketRegion == MarketRegion.INDIA
            ? accountProperties.getIndiaStartingBalance()
            : accountProperties.getUnitedStatesStartingBalance();
    }

    private PricingStatus combine(PricingStatus current, PricingStatus next) {
        if (current == PricingStatus.UNAVAILABLE || next == PricingStatus.UNAVAILABLE) {
            return PricingStatus.UNAVAILABLE;
        }
        if (current == PricingStatus.STALE || next == PricingStatus.STALE) {
            return PricingStatus.STALE;
        }
        return PricingStatus.LIVE;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal value, BigDecimal base) {
        if (base.signum() == 0) {
            return money(BigDecimal.ZERO);
        }
        return value.multiply(HUNDRED).divide(base, 4, RoundingMode.HALF_UP);
    }
}
