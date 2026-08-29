package com.stoxsim.order.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stoxsim.account.api.LedgerEntryResponse;
import com.stoxsim.account.repository.AccountLedgerRepository;
import com.stoxsim.account.service.AccountService;
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
    private final AccountService accounts;

    public TradingQueryService(
        HoldingRepository holdings,
        TradeRepository trades,
        AccountLedgerRepository ledger,
        AccountService accounts
    ) {
        this.holdings = holdings;
        this.trades = trades;
        this.ledger = ledger;
        this.accounts = accounts;
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

    @Transactional(readOnly = true)
    public List<HoldingResponse> holdingsForAccount(UUID userId, UUID accountId) {
        accounts.requireOwned(userId, accountId);
        return holdings.findAllOwnedByAccountIdAndQuantityGreaterThan(
            userId,
            accountId,
            0
        ).stream().map(HoldingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> tradesForAccount(UUID userId, UUID accountId) {
        accounts.requireOwned(userId, accountId);
        return trades.findAllOwnedByAccountId(userId, accountId)
            .stream()
            .map(TradeResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> ledgerForAccount(UUID userId, UUID accountId) {
        accounts.requireOwned(userId, accountId);
        return ledger.findAllOwnedByAccountId(userId, accountId)
            .stream()
            .map(LedgerEntryResponse::from)
            .toList();
    }
}
