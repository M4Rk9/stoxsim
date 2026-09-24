package com.stoxsim.portfolio.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.calendar.service.IndiaMarketSessionService;
import com.stoxsim.subscription.domain.SubscriptionFeature;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;

@Service
public class PortfolioHistoryService {
    public record Point(LocalDate day,Instant observedAt,BigDecimal equity,String quality,Double portfolioIndex,Double benchmarkIndex) {}
    public record History(String currency,String tier,int days,String benchmark,String status,int missingSessions,int cashFlowBreaks,
        Double returnPercent,Double benchmarkReturnPercent,Double maximumDrawdownPercent,HistoryMath.Risk risk,List<Point> points) {}
    private record Row(LocalDate day,Instant at,BigDecimal equity,BigDecimal cash,BigDecimal tradeCash,BigDecimal capital,String quality,BigDecimal benchmark) {}
    private final JdbcTemplate db;
    private final VirtualAccountRepository accounts;
    private final UserSubscriptionRepository subscriptions;
    private final IndiaMarketSessionService sessions;
    private final Clock clock;
    @Value("${stoxsim.portfolio.history.benchmark-retention-enabled:false}")
    private boolean benchmarkRetentionEnabled;
    public PortfolioHistoryService(JdbcTemplate db,VirtualAccountRepository accounts,UserSubscriptionRepository subscriptions,IndiaMarketSessionService sessions,Clock clock) {
        this.db=db;this.accounts=accounts;this.subscriptions=subscriptions;this.sessions=sessions;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public History history(UUID user,UUID accountId,int days) {
        if(days!=30 && days!=90 && days!=365) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose 30, 90 or 365 days");
        var account=accounts.findOwnedById(user,accountId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found"));
        var subscription=subscriptions.findByUserId(user).orElse(null);
        boolean plus=subscription!=null && subscription.hasActiveEntitlement(SubscriptionFeature.ADVANCED_ANALYTICS);
        boolean pro=subscription!=null && subscription.hasActiveEntitlement(SubscriptionFeature.ADVANCED_RISK_ANALYTICS);
        if(!plus && days>30) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Longer history requires active Plus or Pro benefits");
        var today=clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        var rows=db.query("SELECT * FROM portfolio_history WHERE account_id=? AND currency=? AND observed_day>=? AND observed_day<=? ORDER BY observed_day",(rs,n)->new Row(
            rs.getDate("observed_day").toLocalDate(),rs.getTimestamp("observed_at").toInstant(),rs.getBigDecimal("equity"),rs.getBigDecimal("cash"),rs.getBigDecimal("trade_cash"),rs.getBigDecimal("capital"),rs.getString("quality"),rs.getBigDecimal("benchmark")),accountId,account.getCurrency(),today.minusDays(days-1),today);
        int missing=0,flows=0;
        boolean valid=rows.size()>=2,benchmarkValid=valid && benchmarkRetentionEnabled;
        var points=new ArrayList<Point>();var returns=new ArrayList<Double>();var benchmarkReturns=new ArrayList<Double>();
        double peak=0,drawdown=0;
        Row first=rows.isEmpty()?null:rows.getFirst(),previous=null;
        for(var row:rows) {
            boolean flow=false;
            if(previous!=null) {
                for(var day=previous.day.plusDays(1);day.isBefore(row.day);day=day.plusDays(1))
                    if(sessions.isTradingDay(PortfolioHistoryCollector.exchange(account.getMarketRegion()),day)) missing++;
                flow=row.capital.compareTo(previous.capital)!=0 || row.cash.subtract(previous.cash).subtract(row.tradeCash.subtract(previous.tradeCash)).abs().compareTo(new BigDecimal("0.0001"))>0;
                if(flow) flows++;
            }
            boolean usable=row.equity!=null && row.equity.signum()>0 && row.quality.equals("OBSERVED");
            if(!usable) { missing++;valid=false; }
            if(row.benchmark==null || row.benchmark.signum()<=0) benchmarkValid=false;
            Double index=usable && first.equity!=null && first.equity.signum()>0?row.equity.doubleValue()/first.equity.doubleValue()*100:null;
            Double bi=plus && row.benchmark!=null && first.benchmark!=null && first.benchmark.signum()>0?row.benchmark.doubleValue()/first.benchmark.doubleValue()*100:null;
            if(index!=null) { peak=Math.max(peak,index);drawdown=Math.max(drawdown,(peak-index)/peak*100); }
            if(previous!=null && usable && previous.equity!=null && previous.equity.signum()>0) returns.add(row.equity.doubleValue()/previous.equity.doubleValue()-1);
            if(previous!=null && row.benchmark!=null && previous.benchmark!=null && previous.benchmark.signum()>0) benchmarkReturns.add(row.benchmark.doubleValue()/previous.benchmark.doubleValue()-1);
            points.add(new Point(row.day,row.at,row.equity,flow?"CASH_FLOW_BREAK":row.quality,plus?index:null,bi));previous=row;
        }
        valid=valid && missing==0 && flows==0;
        // Never draw a continuous normalized line across unknown values or cash adjustments.
        if(!valid) points.replaceAll(p->new Point(p.day,p.observedAt,p.equity,p.quality,null,null));
        if(!benchmarkValid) points.replaceAll(p->new Point(p.day,p.observedAt,p.equity,p.quality,p.portfolioIndex,null));
        Double result=valid && plus?(rows.getLast().equity.doubleValue()/first.equity.doubleValue()-1)*100:null;
        Double benchmarkResult=valid && plus && benchmarkValid?(rows.getLast().benchmark.doubleValue()/first.benchmark.doubleValue()-1)*100:null;
        var risk=valid && pro?HistoryMath.risk(returns,benchmarkValid?benchmarkReturns:List.of()):new HistoryMath.Risk(null,null,null);
        String status=rows.isEmpty()?"COLLECTING":!valid?(flows>0?"CASH_FLOW_BREAK":missing>0?"DATA_GAPS":"INSUFFICIENT_HISTORY"):returns.size()<20?"LIMITED_HISTORY":"READY";
        return new History(account.getCurrency(),pro?"PRO":plus?"PLUS":"FREE",days,account.getMarketRegion().name().equals("INDIA")?"NIFTY 50 price index":"SPY price (excludes dividends)",status,missing,flows,result,benchmarkResult,valid&&plus?drawdown:null,risk,List.copyOf(points));
    }
}
