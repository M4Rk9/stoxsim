package com.stoxsim.portfolio.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.calendar.service.IndiaMarketSessionService;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.service.IndexQuoteService;
import com.stoxsim.market.service.MarketDataService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
public class PortfolioHistoryCollector {
    private final JdbcTemplate db;
    private final VirtualAccountRepository accounts;
    private final PortfolioValuationService valuation;
    private final IndiaMarketSessionService sessions;
    private final Clock clock;
    private final IndexQuoteService indexes;
    private final MarketDataService market;
    @Value("${stoxsim.portfolio.history.benchmark-retention-enabled:false}")
    private boolean benchmarkRetentionEnabled;
    public PortfolioHistoryCollector(JdbcTemplate db,VirtualAccountRepository accounts,PortfolioValuationService valuation,
        IndiaMarketSessionService sessions,Clock clock,IndexQuoteService indexes,MarketDataService market) {
        this.db=db;this.accounts=accounts;this.valuation=valuation;this.sessions=sessions;this.clock=clock;this.indexes=indexes;this.market=market;
    }
    public static ZoneId zone(MarketRegion region) { return ZoneId.of(region==MarketRegion.INDIA?"Asia/Kolkata":"America/New_York"); }
    public static MarketExchange exchange(MarketRegion region) { return region==MarketRegion.INDIA?MarketExchange.NSE:MarketExchange.NASDAQ; }
    @Transactional(timeout=30)
    public void capture(UUID id) {
        // Global observation window is after both regular sessions. No intraday backfill.
        var now=clock.instant();
        if(now.atZone(ZoneId.of("UTC")).getHour()!=23) return;
        var account=accounts.findByIdForUpdate(id).orElse(null);
        if(account==null) return;
        var region=account.getMarketRegion();
        // India is already on the next local date at 23:40 UTC: observe the UTC day's session.
        LocalDate day=now.atZone(ZoneId.of("UTC")).toLocalDate();
        if(!sessions.isTradingDay(exchange(region),day)) return;
        if(db.queryForObject("SELECT count(*) FROM portfolio_history WHERE account_id=? AND observed_day=?",Integer.class,id,day)>0) return;
        var value=valuation.valueForAccount(account.getUserId(),id);
        boolean valid=value.holdings().stream().allMatch(p->
            (p.pricingStatus().name().equals("LIVE")||p.pricingStatus().name().equals("CLOSED"))
            && p.priceTimestamp()!=null && !p.priceTimestamp().isAfter(now)
            && p.priceTimestamp().atZone(zone(region)).toLocalDate().equals(day)
            && p.currency().equals(account.getCurrency()));
        BigDecimal benchmark=null;
        try { if(benchmarkRetentionEnabled) {
            if(region==MarketRegion.INDIA) {
                var quote=indexes.current().stream().filter(q->q.code().equals("NIFTY_50")).findFirst().orElseThrow();
                if(quote.exchangeTimestamp()!=null && quote.exchangeTimestamp().atZone(zone(region)).toLocalDate().equals(day)
                    && !quote.exchangeTimestamp().isAfter(now) && (quote.dataStatus().name().equals("LIVE")||quote.dataStatus().name().equals("CLOSED"))) benchmark=quote.value();
            } else {
                var quote=market.getQuote(region,MarketExchange.NYSE_ARCA,"SPY");
                var timestamp=quote.exchangeTimestamp();
                if(timestamp!=null && timestamp.atZone(zone(region)).toLocalDate().equals(day) && !timestamp.isAfter(now)
                    && (quote.dataStatus().name().equals("LIVE")||quote.dataStatus().name().equals("CLOSED"))) benchmark=quote.lastPrice();
            }
        } } catch(RuntimeException unavailable) { /* Missing benchmark must not hide portfolio history. */ }
        if(benchmark!=null && benchmark.signum()<=0) benchmark=null;
        var tradeCash=db.queryForObject("SELECT COALESCE(SUM(CASE WHEN direction='CREDIT' THEN amount ELSE -amount END),0) FROM account_ledger WHERE account_id=?",BigDecimal.class,id);
        db.update("INSERT INTO portfolio_history(account_id,observed_day,observed_at,currency,equity,cash,trade_cash,capital,quality,benchmark) VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
            id,day,Timestamp.from(now),account.getCurrency(),valid?value.totalAccountValue():null,
            account.getAvailableCash().add(account.getBlockedCash()),tradeCash,account.getStartingCapital(),valid?"OBSERVED":"MISSING_PRICES",benchmark);
    }
}
