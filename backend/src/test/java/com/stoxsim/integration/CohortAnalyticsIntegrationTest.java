package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.stoxsim.analytics.repository.CohortRepository;
import com.stoxsim.analytics.repository.CohortRepository.Group;

@Testcontainers
class CohortAnalyticsIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    JdbcTemplate db; CohortRepository repo;
    static final Instant NOW=Instant.parse("2026-11-15T12:00:00Z");
    static final Instant SIGNUP=Instant.parse("2026-11-01T00:00:00Z");
    static final Instant COVERAGE=Instant.parse("2026-10-01T00:00:00Z");
    @BeforeEach void setup() throws Exception {
        db=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
        db.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        db.execute("""
            CREATE TABLE app_user(id uuid PRIMARY KEY,created_at timestamptz NOT NULL,platform_role text NOT NULL DEFAULT 'USER',is_test_account boolean NOT NULL DEFAULT false);
            CREATE TABLE virtual_account(id uuid PRIMARY KEY,user_id uuid REFERENCES app_user(id) ON DELETE CASCADE,account_kind text DEFAULT 'STANDARD');
            CREATE TABLE paper_order(id uuid PRIMARY KEY,account_id uuid REFERENCES virtual_account(id) ON DELETE CASCADE,status text,executed_at timestamptz);
            CREATE TABLE decision_reflection(id uuid PRIMARY KEY,user_id uuid REFERENCES app_user(id) ON DELETE CASCADE,account_id uuid,order_id uuid);
            """);
        migrate("V111__product_activity.sql");migrate("V133__strategy_review_journey.sql");migrate("V134__signup_cohort_dimensions.sql");
        repo=new CohortRepository(db);
    }
    void migrate(String name) throws Exception {
        try(var stream=getClass().getResourceAsStream("/db/migration/"+name)){db.execute(new String(stream.readAllBytes(),StandardCharsets.UTF_8));}
    }
    UUID user(Instant signup,String role,boolean test) {
        var id=UUID.randomUUID();db.update("INSERT INTO app_user VALUES (?,?,?,?)",id,Timestamp.from(signup),role,test);return id;
    }
    void event(UUID user,String kind,int day) {
        var at=SIGNUP.plusSeconds(day*86400L+3600);
        db.update("INSERT INTO product_activity(user_id,event_day,event_name,first_at) VALUES (?,?,?,?)",user,
            java.sql.Date.valueOf(at.atZone(ZoneOffset.UTC).toLocalDate()),kind,Timestamp.from(at));
    }
    java.util.List<com.stoxsim.analytics.api.CohortOverview.Row> rows(Group group) {
        return repo.rows(group,COVERAGE,NOW,COVERAGE,COVERAGE,NOW,LocalDate.of(2026,11,15));
    }
    @Test void windowsHaveIndependentMaturityAndExcludeAdminTestDeletedAndBackgroundFills() {
        var learner=user(SIGNUP,"USER",false);event(learner,"ACTIVE",1);event(learner,"ACTIVE",6);event(learner,"ACTIVE",8);
        var fillOnly=user(SIGNUP,"USER",false);event(fillOnly,"ORDER_EXECUTED",7);
        var immature=user(Instant.parse("2026-11-14T00:00:00Z"),"USER",false);
        event(immature,"ACTIVE",14); // Today must not count even though an event exists.
        for(var excluded:new UUID[]{user(SIGNUP,"ADMIN",false),user(SIGNUP,"USER",true)}) event(excluded,"ACTIVE",7);
        var deleted=user(SIGNUP,"USER",false);event(deleted,"ACTIVE",7);db.update("DELETE FROM app_user WHERE id=?",deleted);
        var row=rows(Group.SOURCE).getFirst();
        assertThat(row.cohort()).isEqualTo("UNKNOWN");assertThat(row.registrations()).isEqualTo(3);
        assertThat(row.d1().eligible()).isEqualTo(2);assertThat(row.d1().immature()).isEqualTo(1);assertThat(row.d1().returned()).isEqualTo(1);
        assertThat(row.d7().returned()).isZero();assertThat(row.days1To7().returned()).isEqualTo(1);
        assertThat(row.followingWeek().eligible()).isZero(); // Day 14 is today, not fully observed.
        assertThat(row.followingWeek().percent()).isNull();
        assertThat(row.journeyEligible()).isEqualTo(2); // 14 elapsed days differ from full UTC return days.
    }
    @Test void linkedCompletionRequiresOrderedResearchStrategyExecutionAndReview() {
        var id=user(SIGNUP,"USER",false);event(id,"STOCK_OPENED",0);event(id,"STRATEGY_CREATED",1);event(id,"ORDER_EXECUTED",2);event(id,"REVIEW_COMPLETED",3);
        var account=UUID.randomUUID();var order=UUID.randomUUID();var note=UUID.randomUUID();
        db.update("INSERT INTO virtual_account(id,user_id) VALUES (?,?)",account,id);
        db.update("INSERT INTO paper_order VALUES (?,?,'EXECUTED',?)",order,account,Timestamp.from(SIGNUP.plusSeconds(2*86400)));
        db.update("INSERT INTO decision_reflection(id,user_id,account_id,order_id,strategy_created_at,reviewed_at) VALUES (?,?,?,?,?,?)",
            note,id,account,order,Timestamp.from(SIGNUP.plusSeconds(86400)),Timestamp.from(SIGNUP.plusSeconds(3*86400)));
        assertThat(rows(Group.WEEK).getFirst().linkedJourneyCompleted()).isEqualTo(1);
        db.update("UPDATE decision_reflection SET reviewed_at=?",Timestamp.from(SIGNUP.plusSeconds(86400)));
        assertThat(rows(Group.WEEK).getFirst().linkedJourneyCompleted()).isZero();
        assertThat(rows(Group.WEEK).getFirst().reviewed()).isEqualTo(1); // Independent marker is not a completed journey.
        db.update("DELETE FROM decision_reflection");assertThat(rows(Group.WEEK).getFirst().linkedJourneyCompleted()).isZero();
    }
    @Test void attributionIsBoundedAndDeletedWithAccountAndCoverageNeverMeansZero() {
        var id=user(SIGNUP,"USER",false);
        db.update("INSERT INTO signup_attribution(user_id,source,device,method) VALUES (?,'SOCIAL','ANDROID_APP','EMAIL')",id);
        assertThat(rows(Group.SOURCE).getFirst().cohort()).isEqualTo("SOCIAL");
        assertThat(rows(Group.DEVICE).getFirst().cohort()).isEqualTo("ANDROID_APP");
        var excluded=repo.rows(Group.WEEK,COVERAGE,NOW,NOW,NOW,NOW,LocalDate.of(2026,11,15)).getFirst();
        assertThat(excluded.d1().excluded()).isEqualTo(1);assertThat(excluded.d1().percent()).isNull();assertThat(excluded.journeyExcluded()).isEqualTo(1);
        db.update("DELETE FROM app_user WHERE id=?",id);
        assertThat(db.queryForObject("SELECT count(*) FROM signup_attribution",Long.class)).isZero();
    }
}
