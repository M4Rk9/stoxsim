package com.stoxsim.finwiz.service;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.stoxsim.finwiz.config.FinwizProperties;

/** Shared atomic budgets count provider attempts, including failed or timed-out requests. */
@Service
public class FinwizUsageBudget {
    private final JdbcTemplate db;
    private final Clock clock;
    private final FinwizProperties properties;
    public FinwizUsageBudget(JdbcTemplate db, Clock clock, FinwizProperties properties) {
        this.db = db; this.clock = clock; this.properties = properties;
    }
    public record Reservation(LocalDate day) { }
    public record RequestReservation(UUID id, UUID user, LocalDate day) { }
    private int userLimit(FinwizDepth depth) {
        return Math.max(0,Math.min(10000,switch(depth) {
            case BASIC -> properties.getBasicDailyProviderRequests();
            case EXPANDED -> properties.getExpandedDailyProviderRequests();
            case FULL -> properties.getFullDailyProviderRequests();
        }));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public RequestReservation reserveRequest(UUID user, FinwizDepth depth) {
        return reserveRequestInternal(user,depth,null);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public RequestReservation reserveRequest(UUID user, FinwizDepth depth, UUID clientRequestId) {
        return reserveRequestInternal(user,depth,clientRequestId);
    }
    private RequestReservation reserveRequestInternal(UUID user, FinwizDepth depth, UUID clientRequestId) {
        if(clientRequestId!=null && db.queryForObject("SELECT count(*) FROM finwiz_request_reservation WHERE user_id=? AND client_request_id=?",Integer.class,user,clientRequestId)>0)
            throw duplicateRequest();
        int limit=userLimit(depth);
        if(limit==0 || properties.getGlobalDailyProviderRequests()<=0) return null;
        Date day=Date.valueOf(clock.instant().atZone(ZoneOffset.UTC).toLocalDate());
        db.update("INSERT INTO finwiz_usage_daily(user_id,usage_day) VALUES(?,?) ON CONFLICT DO NOTHING",user,day);
        int successes=db.queryForObject("SELECT successful_requests FROM finwiz_usage_daily WHERE user_id=? AND usage_day=? FOR UPDATE",Integer.class,user,day);
        int pending=db.queryForObject("SELECT count(*) FROM finwiz_request_reservation WHERE user_id=? AND usage_day=? AND state='PENDING' AND expires_at>now()",Integer.class,user,day);
        if(successes+pending>=limit) return null;
        UUID id=UUID.randomUUID();
        int inserted=db.update("INSERT INTO finwiz_request_reservation(id,user_id,usage_day,state,expires_at,client_request_id) VALUES(?,?,?,'PENDING',now()+interval '120 seconds',?) ON CONFLICT(user_id,client_request_id) DO NOTHING",id,user,day,clientRequestId);
        if(inserted!=1) throw duplicateRequest();
        return new RequestReservation(id,user,day.toLocalDate());
    }
    private org.springframework.web.server.ResponseStatusException duplicateRequest() {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
            "This Finwiz request has already been submitted. Check your conversation, or submit a new question.");
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Reservation reserveAttempt(RequestReservation request, FinwizDepth depth, String provider, int providerLimit, int rpm, int tpm, int tpd, int estimatedTokens) {
        if (!java.util.Set.of("GEMINI","GROQ","CLOUDFLARE","OPENROUTER").contains(provider)) throw new IllegalArgumentException("Unknown provider");
        int globalLimit=Math.max(0,Math.min(100000,properties.getGlobalDailyProviderRequests()));
        providerLimit=Math.max(0,Math.min(100000,providerLimit));
        int userAttemptLimit=userLimit(depth)*4;
        if(globalLimit==0||providerLimit==0||userAttemptLimit==0||rpm<=0||tpm<=0||tpd<=0||estimatedTokens<=0||estimatedTokens>tpm||estimatedTokens>tpd) return null;
        Date day=Date.valueOf(request.day());
        if(!request.day().equals(clock.instant().atZone(ZoneOffset.UTC).toLocalDate())) return null;
        db.update("INSERT INTO finwiz_global_usage_daily(usage_day) VALUES(?) ON CONFLICT DO NOTHING",day);
        int global=db.queryForObject("SELECT provider_attempts FROM finwiz_global_usage_daily WHERE usage_day=? FOR UPDATE",Integer.class,day);
        if(global>=globalLimit) return null;
        var used=db.query("SELECT provider_attempts FROM finwiz_usage_daily WHERE user_id=? AND usage_day=? FOR UPDATE",(rs,n)->rs.getInt(1),request.user(),day);
        if(used.isEmpty()||used.getFirst()>=userAttemptLimit) return null;
        if(db.queryForObject("SELECT count(*) FROM finwiz_request_reservation WHERE id=? AND user_id=? AND usage_day=? AND state='PENDING' AND expires_at>now()",Integer.class,request.id(),request.user(),day)!=1) return null;
        db.update("INSERT INTO finwiz_provider_usage_daily(provider,usage_day) VALUES(?,?) ON CONFLICT DO NOTHING",provider,day);
        int providerUsed=db.queryForObject("SELECT provider_attempts FROM finwiz_provider_usage_daily WHERE provider=? AND usage_day=? FOR UPDATE",Integer.class,provider,day);
        if(providerUsed>=providerLimit) return null;
        long dailyTokens=db.queryForObject("SELECT reserved_tokens FROM finwiz_provider_usage_daily WHERE provider=? AND usage_day=?",Long.class,provider,day);
        if(dailyTokens+estimatedTokens>tpd) return null;
        // Global-day lock serialises admission; rolling database-time window avoids minute-boundary bursts.
        // Previous-day events are included in this query at UTC midnight.
        var recent=db.queryForMap("SELECT count(*) AS attempts, coalesce(sum(estimated_tokens),0) AS tokens FROM finwiz_provider_admission WHERE provider=? AND admitted_at>now()-interval '60 seconds'",provider);
        if(((Number)recent.get("attempts")).longValue()>=rpm || ((Number)recent.get("tokens")).longValue()+estimatedTokens>tpm) return null;
        db.update("INSERT INTO finwiz_provider_admission(provider,estimated_tokens) VALUES(?,?)",provider,estimatedTokens);
        db.update("UPDATE finwiz_provider_usage_daily SET provider_attempts=provider_attempts+1,reserved_tokens=reserved_tokens+? WHERE provider=? AND usage_day=?",estimatedTokens,provider,day);
        db.update("UPDATE finwiz_usage_daily SET provider_attempts=provider_attempts+1 WHERE user_id=? AND usage_day=?",request.user(),day);
        db.update("UPDATE finwiz_global_usage_daily SET provider_attempts=provider_attempts+1 WHERE usage_day=?",day);
        return new Reservation(request.day());
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public boolean finishRequest(RequestReservation request, boolean success) {
        Date day=Date.valueOf(request.day());
        var rows=db.query("SELECT successful_requests FROM finwiz_usage_daily WHERE user_id=? AND usage_day=? FOR UPDATE",(rs,n)->rs.getInt(1),request.user(),day);
        if(rows.isEmpty()) return false;
        int changed=db.update("UPDATE finwiz_request_reservation SET state=? WHERE id=? AND user_id=? AND usage_day=? AND state='PENDING' AND expires_at>now()",success?"SUCCEEDED":"FAILED",request.id(),request.user(),day);
        if(changed!=1) return false;
        if(success) db.update("UPDATE finwiz_usage_daily SET successful_requests=successful_requests+1 WHERE user_id=? AND usage_day=?",request.user(),day);
        return true;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void recordReportedTokens(LocalDate reservedDay, String provider, long tokens) {
        if(tokens<0||tokens>1000000) return;
        Date day=Date.valueOf(reservedDay);
        db.update("UPDATE finwiz_global_usage_daily SET reported_total_tokens=reported_total_tokens+?, responses_with_usage=responses_with_usage+1 WHERE usage_day=?",tokens,day);
        db.update("UPDATE finwiz_provider_usage_daily SET reported_total_tokens=reported_total_tokens+?, responses_with_usage=responses_with_usage+1 WHERE provider=? AND usage_day=?",tokens,provider,day);
    }
    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    @Transactional(timeout = 10)
    public void purgeExpired() {
        Date cutoff = Date.valueOf(clock.instant().atZone(ZoneOffset.UTC).toLocalDate().minusDays(179));
        db.update("DELETE FROM finwiz_provider_admission WHERE admitted_at<now()-interval '1 day'");
        db.update("DELETE FROM finwiz_request_reservation WHERE expires_at<now()-interval '1 day'");
        db.update("DELETE FROM finwiz_usage_daily WHERE ctid IN(SELECT ctid FROM finwiz_usage_daily WHERE usage_day<? LIMIT 5000)", cutoff);
        db.update("DELETE FROM finwiz_global_usage_daily WHERE usage_day<?", cutoff);
        db.update("DELETE FROM finwiz_provider_usage_daily WHERE usage_day<?", cutoff);
    }
}
