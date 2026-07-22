package com.stoxsim.order.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stoxsim.account.api.LedgerEntryResponse;
import com.stoxsim.account.repository.AccountLedgerRepository;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.HoldingResponse;
import com.stoxsim.portfolio.repository.HoldingRepository;
import com.stoxsim.trade.api.TradeResponse;
import com.stoxsim.trade.repository.TradeRepository;

@Service
public class TradingQueryService {

    private final HoldingRepository holdings;
    private final TradeRepository trades;
    private final AccountLedgerRepository ledger;

    public TradingQueryService(
        HoldingRepository holdings,
        TradeRepository trades,
        AccountLedgerRepository ledger
    ) {
        this.holdings = holdings;
        this.trades = trades;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public List<HoldingResponse> holdings(UUID userId, MarketRegion marketRegion) {
        return holdings
            .findAllByAccountUserIdAndAccountMarketRegionAndQuantityGreaterThanOrderByInstrumentTradingSymbol(
                userId,
                marketRegion,
                0
            )
            .stream()
            .map(HoldingResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> trades(UUID userId, MarketRegion marketRegion) {
        return trades
            .findAllByAccountUserIdAndAccountMarketRegionOrderByExecutedAtDesc(
                userId,
                marketRegion
            )
            .stream()
            .map(TradeResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> ledger(UUID userId, MarketRegion marketRegion) {
        return ledger
            .findAllByAccountUserIdAndAccountMarketRegionOrderByCreatedAtDesc(
                userId,
                marketRegion
            )
            .stream()
            .map(LedgerEntryResponse::from)
            .toList();
    }
}
