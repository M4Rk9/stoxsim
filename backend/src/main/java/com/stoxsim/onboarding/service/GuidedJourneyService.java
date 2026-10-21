package com.stoxsim.onboarding.service;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;

/** Server-observed progress shared by web and Android. Opening a card never completes a task. */
@Service
public class GuidedJourneyService {
    public enum Event { FINWIZ, LEARNING, COMPETITIONS }
    public record State(boolean firstTradeExecuted, boolean awaitingExecution, boolean finwizAsked,
        boolean scenarioRun, boolean learningViewed, boolean competitionsViewed,
        String nextStep, int completedSteps, int totalSteps, boolean completed) {}
    private final JdbcTemplate db;
    private final AppUserRepository users;
    private final com.stoxsim.analytics.service.ProductActivityService activity;
    public GuidedJourneyService(JdbcTemplate db, AppUserRepository users,
            com.stoxsim.analytics.service.ProductActivityService activity) {
        this.db = db; this.users = users; this.activity = activity;
    }

    @Transactional
    public void record(UUID user, Event event) {
        db.update("INSERT INTO guided_journey_event(user_id, step) VALUES (?, ?) ON CONFLICT DO NOTHING", user, event.name());
        activity.record(user, switch (event) {
            case FINWIZ -> com.stoxsim.analytics.service.ProductActivityEvent.Kind.FINWIZ_USED;
            case LEARNING -> com.stoxsim.analytics.service.ProductActivityEvent.Kind.LEARNING_VIEWED;
            case COMPETITIONS -> com.stoxsim.analytics.service.ProductActivityEvent.Kind.COMPETITIONS_VIEWED;
        });
    }

    @Transactional(readOnly = true)
    public State state(UUID userId) {
        var user = users.findById(userId).orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        boolean executed = exists("SELECT EXISTS (SELECT 1 FROM trade t JOIN virtual_account a ON a.id=t.account_id WHERE a.user_id=? AND a.account_kind='STANDARD')", userId);
        boolean pending = !executed && exists("SELECT EXISTS (SELECT 1 FROM paper_order o JOIN virtual_account a ON a.id=o.account_id WHERE a.user_id=? AND a.account_kind='STANDARD' AND o.status='OPEN')", userId);
        boolean scenario = exists("SELECT EXISTS (SELECT 1 FROM scenario_run WHERE user_id=?)", userId);
        var events = db.queryForList("SELECT step FROM guided_journey_event WHERE user_id=?", String.class, userId);
        return progress(user.getOnboardingIntroCompletedAt() != null, executed, pending,
            events.contains("FINWIZ"), scenario, events.contains("LEARNING"), events.contains("COMPETITIONS"));
    }

    private boolean exists(String sql, UUID user) { return Boolean.TRUE.equals(db.queryForObject(sql, Boolean.class, user)); }

    static State progress(boolean intro, boolean executed, boolean pending, boolean finwiz,
                          boolean scenario, boolean learning, boolean competitions) {
        int done = (intro ? 1 : 0) + (executed ? 1 : 0) + (finwiz ? 1 : 0)
            + (scenario ? 1 : 0) + (learning ? 1 : 0) + (competitions ? 1 : 0);
        String next = !intro ? "INTRODUCTION" : !executed ? (pending ? "WAITING" : "FIRST_TRADE")
            : !finwiz ? "FINWIZ" : !scenario ? "SCENARIO" : !learning ? "LEARNING"
            : !competitions ? "COMPETITIONS" : "COMPLETE";
        return new State(executed, pending, finwiz, scenario, learning, competitions, next, done, 6, done == 6);
    }
}
