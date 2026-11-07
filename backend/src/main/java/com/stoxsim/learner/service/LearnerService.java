package com.stoxsim.learner.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.analytics.service.ProductActivityEvent.Kind;
import com.stoxsim.analytics.service.ProductActivityService;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;
import com.stoxsim.learner.api.LearnerHome;
import com.stoxsim.learner.api.LearnerHome.Recommendation;
import com.stoxsim.learner.api.LearnerProfile;
import com.stoxsim.learner.api.LearnerProfile.ExperienceLevel;
import com.stoxsim.learner.api.LearnerProfile.LearningObjective;
import com.stoxsim.market.domain.MarketRegion;

@Service
public class LearnerService {
    private final JdbcTemplate db;
    private final AppUserRepository users;
    private final ProductActivityService activity;
    private final Clock clock;

    public LearnerService(JdbcTemplate db, AppUserRepository users, ProductActivityService activity, Clock clock) {
        this.db = db; this.users = users; this.activity = activity; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LearnerProfile profile(UUID user) {
        requireUser(user);
        return db.query("SELECT * FROM learner_profile WHERE user_id=?", (rs, row) -> new LearnerProfile(
            value(ExperienceLevel.class, rs.getString("experience_level")),
            value(LearningObjective.class, rs.getString("learning_objective")),
            value(MarketRegion.class, rs.getString("preferred_market")), rs.getTimestamp("updated_at").toInstant()), user)
            .stream().findFirst().orElseGet(LearnerProfile::empty);
    }

    @Transactional
    public LearnerProfile update(UUID user, LearnerProfile.Update request) {
        requireUser(user);
        db.update("""
            INSERT INTO learner_profile(user_id,experience_level,learning_objective,preferred_market,updated_at)
            VALUES (?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET experience_level=excluded.experience_level,
                learning_objective=excluded.learning_objective,preferred_market=excluded.preferred_market,updated_at=excluded.updated_at
            """, user, name(request.experienceLevel()), name(request.learningObjective()), name(request.preferredMarket()), Timestamp.from(clock.instant()));
        return profile(user);
    }

    @Transactional(readOnly = true)
    public LearnerHome home(UUID user) {
        var profile = profile(user);
        // Stored simulator facts only: Home never waits for a quote or AI provider.
        if (!exists("SELECT EXISTS(SELECT 1 FROM trade t JOIN virtual_account a ON a.id=t.account_id WHERE a.user_id=? AND a.account_kind='STANDARD')", user)) {
            boolean pending = exists("SELECT EXISTS(SELECT 1 FROM paper_order o JOIN virtual_account a ON a.id=o.account_id WHERE a.user_id=? AND a.account_kind='STANDARD' AND o.status='OPEN')", user);
            return home(profile, pending ? new Recommendation("REVIEW_PENDING_ORDER", "Understand your pending order",
                "An open order has not executed yet. Review its status and learn how price constraints and market sessions affect execution.", "/#orders", "Review orders")
                : new Recommendation("FIRST_TRADE", "Explore your first paper trade",
                "Use the walkthrough to understand an order with virtual funds. You decide whether and when to submit it.", "/#stock-search", "Explore markets"));
        }
        boolean concentrated = exists("""
            SELECT EXISTS(SELECT 1 FROM holding h JOIN virtual_account a ON a.id=h.account_id
                WHERE a.user_id=? AND a.account_kind='STANDARD' AND h.quantity>0
                GROUP BY a.id HAVING sum(h.quantity*h.average_price)>0
                    AND max(h.quantity*h.average_price)/sum(h.quantity*h.average_price)>0.5)
            """, user);
        if (concentrated) return home(profile, new Recommendation("DIVERSIFICATION", "Explore concentration and diversification",
            "One holding represents more than half the recorded purchase cost in a standard portfolio. Cost weights differ from current market values; explore what diversification can and cannot tell you.", "/portfolio", "Review portfolio structure"));
        if (exists("SELECT EXISTS(SELECT 1 FROM competition_entry e JOIN competition_season s ON s.id=e.season_id WHERE e.user_id=? AND s.ends_at>CURRENT_TIMESTAMP)", user))
            return home(profile, new Recommendation("COMPETITION", "Review your learning competition",
                "Check the season and reflect on your process. Participation and simulated returns do not require more frequent trading.", "/competitions", "View competition"));
        if (profile.learningObjective() == LearningObjective.FUNDAMENTALS)
            return home(profile, new Recommendation("FUNDAMENTALS", "Practise reading financial statements",
                "Explore how profit, cash flow and financial position answer different questions. Separate reported facts from assumptions.", "/finwiz", "Explore fundamentals"));
        if (profile.learningObjective() == LearningObjective.DISCIPLINE)
            return home(profile, new Recommendation("REFLECTION", "Reflect on a simulated decision",
                "Record the reason, assumptions and risks behind one decision. A useful reflection is about the process, not the return.", "/learn#reflections", "Write a reflection"));
        if (!exists("SELECT EXISTS(SELECT 1 FROM guided_journey_event WHERE user_id=? AND step='LEARNING')", user))
            return home(profile, new Recommendation("CONTINUE_LEARNING", "Continue your learning path",
                "Review your progress and choose one unfinished learning activity. Your progress is shared across devices.", "/progress", "Continue learning"));
        return home(profile, new Recommendation("SCENARIO", "Explore a portfolio scenario",
            "Compare a hypothetical scenario with your assumptions. Scenarios are educational exercises, not forecasts.", "/scenario-lab", "Explore Scenario Lab"));
    }

    @Transactional
    public void used(UUID user, String id) {
        // Only the recommendation actually eligible now can be recorded. A click never completes a mission or changes financial state.
        if (!home(user).recommendation().id().equals(id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Recommendation has changed; refresh your home page");
        activity.record(user, Kind.RECOMMENDATION_USED);
    }

    private LearnerHome home(LearnerProfile profile, Recommendation recommendation) { return new LearnerHome(profile, recommendation); }
    private boolean exists(String sql, UUID user) { return Boolean.TRUE.equals(db.queryForObject(sql, Boolean.class, user)); }
    private void requireUser(UUID user) { if (!users.existsById(user)) throw new UnauthorizedException("User no longer exists"); }
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
    private static <E extends Enum<E>> E value(Class<E> type, String value) { return value == null ? null : Enum.valueOf(type, value); }
}
