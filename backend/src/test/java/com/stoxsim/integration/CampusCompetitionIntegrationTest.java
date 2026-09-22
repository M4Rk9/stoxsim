package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.AccountLifecycleService;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.campus.api.CampusCompetitionDtos.*;
import com.stoxsim.campus.service.CampusCompetitionService;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.PortfolioResponse;
import com.stoxsim.portfolio.api.PortfolioPositionResponse;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.service.PortfolioValuationService;
import com.stoxsim.subscription.domain.SubscriptionPlan;

@Testcontainers
@SpringBootTest(properties={"stoxsim.market-data.upstox.stream-enabled=false", "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false", "spring.task.scheduling.enabled=false"})
class CampusCompetitionIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired VirtualAccountRepository accounts;
    @Autowired CampusCompetitionService service;
    @Autowired AccountLifecycleService lifecycle;
    @Autowired WebApplicationContext context;
    @MockitoBean PortfolioValuationService valuations;
    AppUser organizer,learner,outsider,admin;
    UUID campus,other;
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE app_user,campus_institution RESTART IDENTITY CASCADE");
        organizer=user("organizer");learner=user("learner");outsider=user("outsider");admin=user("admin");
        jdbc.update("UPDATE app_user SET platform_role='ADMIN' WHERE id=?",admin.getId());
        campus=institution("First campus");other=institution("Other campus");
        member(campus,organizer,"ORGANIZER");member(other,outsider,"ORGANIZER");
        for(var user:List.of(organizer,learner,outsider,admin)) {
            accounts.saveAndFlush(new VirtualAccount(user,MarketRegion.INDIA,new BigDecimal("500000")));
            value(user,"500000",PricingStatus.UNAVAILABLE,false);
        }
    }
    @Test void membershipRequiresVerificationReviewAndInstitutionScope() {
        jdbc.update("UPDATE app_user SET email_verified_at=NULL WHERE id=?",learner.getId());
        expectStatus(409,()->service.apply(learner.getId(),campus,"Finance club"));
        jdbc.update("UPDATE app_user SET email_verified_at=now() WHERE id=?",learner.getId());
        var request=service.apply(learner.getId(),campus,"Finance club");
        assertThat(service.apply(learner.getId(),campus,"Retry").id()).isEqualTo(request.id());
        expectStatus(409,()->service.apply(learner.getId(),other,"Other club"));
        expectStatus(404,()->service.review(outsider.getId(),campus,request.id(),new Review(true,"Checked")));
        expectStatus(404,()->service.review(outsider.getId(),other,request.id(),new Review(true,"Checked")));
        service.review(organizer.getId(),campus,request.id(),new Review(true,"Affiliation checked"));
        assertThat(service.workspace(learner.getId(),campus).viewerRole()).isEqualTo("MEMBER");
        expectStatus(409,()->service.review(organizer.getId(),campus,request.id(),new Review(false,"Retry")));
        expectStatus(404,()->service.management(learner.getId(),campus));
        expectStatus(404,()->service.workspace(learner.getId(),other));
    }
    @Test void cancelAndRejectRequestsAllowAnotherApplication() {
        var request=service.apply(learner.getId(),campus,"Club");
        expectStatus(404,()->service.cancelRequest(outsider.getId(),request.id()));
        service.cancelRequest(learner.getId(),request.id());
        var next=service.apply(learner.getId(),other,"New club");
        service.review(outsider.getId(),other,next.id(),new Review(false,"Please confirm your course"));
        assertThat(service.latest(learner.getId()).reviewNote()).isEqualTo("Please confirm your course");
        assertThat(service.apply(learner.getId(),campus,"Course confirmed").status()).isEqualTo("PENDING");
    }
    @Test void lastOrganizerAndRevokedRolesAreEnforcedFromDatabase() {
        member(campus,learner,"MEMBER");
        expectStatus(409,()->service.changeRole(organizer.getId(),campus,organizer.getId(),"MEMBER"));
        expectStatus(409,()->service.removeMember(organizer.getId(),campus,organizer.getId(),"Leaving"));
        service.changeRole(organizer.getId(),campus,learner.getId(),"ORGANIZER");
        service.changeRole(learner.getId(),campus,organizer.getId(),"MEMBER");
        expectStatus(404,()->service.management(organizer.getId(),campus));
        jdbc.update("UPDATE app_user SET platform_role='USER' WHERE id=?",admin.getId());
        expectStatus(404,()->service.suspend(admin.getId(),campus,new Suspension(true,"Review")));
    }
    @Test void adminsRecoverOrphanedCampusAndSuspensionStopsParticipation() {
        users.deleteById(organizer.getId());
        service.recoverOrganizer(admin.getId(),campus,new Organizer(learner.getEmail(),"Affiliation rechecked"));
        assertThat(service.management(learner.getId(),campus).members()).hasSize(1);
        var event=create(learner,campus,10);
        service.enroll(learner.getId(),campus,event.id());
        service.suspend(admin.getId(),campus,new Suspension(true,"Review"));
        assertThat(service.directory(learner.getId(),"First")).isEmpty();
        assertThat(service.directory(admin.getId(),"First")).hasSize(1);
        value(learner,"600000",PricingStatus.LIVE,false);
        assertThat(service.board(learner.getId(),campus,event.id()).yourLatestValue()).isEqualByComparingTo("500000");
        expectStatus(409,()->create(learner,campus,10));
        expectStatus(409,()->service.apply(admin.getId(),campus,"Club"));
        service.suspend(admin.getId(),campus,new Suspension(false,"Resolved"));
        assertThat(service.board(learner.getId(),campus,event.id()).standings().getFirst().returnPercent()).isEqualByComparingTo("20");
        service.withdraw(learner.getId(),campus,event.id());
    }
    @Test void enrollmentUsesStandardAccountAndNeverResetsBaselineOrEnrollsGlobally() {
        member(campus,learner,"MEMBER");
        var sandbox=accounts.saveAndFlush(VirtualAccount.sandbox(learner,SubscriptionPlan.PRO,1,new BigDecimal("1000000")));
        var event=create(organizer,campus,10);
        service.enroll(learner.getId(),campus,event.id());
        value(learner,"550000",PricingStatus.LIVE,false);
        assertThat(service.enroll(learner.getId(),campus,event.id()).yourBaselineValue()).isEqualByComparingTo("500000");
        var board=service.board(learner.getId(),campus,event.id());
        assertThat(board.standings().getFirst().returnPercent()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT account_id FROM campus_competition_entry WHERE user_id=?",UUID.class,learner.getId())).isNotEqualTo(sandbox.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM competition_entry WHERE user_id=?",Long.class,learner.getId())).isZero();
        service.removeMember(organizer.getId(),campus,learner.getId(),"Leaving club");
        expectStatus(404,()->service.board(learner.getId(),campus,event.id()));
        var request=service.apply(learner.getId(),campus,"Returning");
        service.review(organizer.getId(),campus,request.id(),new Review(true,"Checked again"));
        expectStatus(409,()->service.enroll(learner.getId(),campus,event.id()));
        assertThat(service.board(organizer.getId(),campus,event.id()).standings()).isEmpty();
    }
    @Test void onlyRequesterRefreshesTiesShareRankAndUnavailableHoldingsKeepPreviousScore() {
        member(campus,learner,"MEMBER");var event=create(organizer,campus,10);
        service.enroll(learner.getId(),campus,event.id());service.enroll(organizer.getId(),campus,event.id());
        assertThat(service.board(organizer.getId(),campus,event.id()).standings()).extracting(s->s.rank()).containsOnly(1);
        value(learner,"550000",PricingStatus.LIVE,false);
        assertThat(service.board(organizer.getId(),campus,event.id()).standings()).extracting(s->s.returnPercent()).allMatch(v->v.signum()==0);
        service.board(learner.getId(),campus,event.id());
        value(learner,"999999",PricingStatus.UNAVAILABLE,true);
        var stale=service.board(learner.getId(),campus,event.id());
        assertThat(stale.refreshUnavailable()).isTrue();assertThat(stale.yourLatestValue()).isEqualByComparingTo("550000");
        assertThat(service.board(admin.getId(),campus,event.id()).yourBaselineValue()).isNull();
    }
    @Test void enrollmentRejectsUnknownPricesAndNonpositiveBaseline() {
        member(campus,learner,"MEMBER");var event=create(organizer,campus,10);
        value(learner,"500000",PricingStatus.UNAVAILABLE,true);
        expectStatus(503,()->service.enroll(learner.getId(),campus,event.id()));
        value(learner,"0",PricingStatus.LIVE,false);
        expectStatus(409,()->service.enroll(learner.getId(),campus,event.id()));
        assertThat(service.board(organizer.getId(),campus,event.id()).standings()).isEmpty();
    }
    @Test void closedScheduledCancelledAndCrossInstitutionCompetitionsRejectEnrollment() {
        member(campus,learner,"MEMBER");var event=create(organizer,campus,10);
        expectStatus(404,()->service.enroll(outsider.getId(),other,event.id()));
        expectStatus(404,()->service.enroll(admin.getId(),campus,event.id()));
        service.enroll(learner.getId(),campus,event.id());
        jdbc.update("UPDATE campus_competition SET starts_at=now()-interval '3 hours',ends_at=now()-interval '1 hour' WHERE id=?",event.id());
        value(learner,"600000",PricingStatus.LIVE,false);
        assertThat(service.board(learner.getId(),campus,event.id()).yourLatestValue()).isEqualByComparingTo("500000");
        expectStatus(409,()->service.enroll(organizer.getId(),campus,event.id()));
        expectStatus(409,()->service.cancelCompetition(organizer.getId(),campus,event.id(),"Cancel"));
        var scheduled=service.create(organizer.getId(),campus,new NewCompetition("Future event",Instant.now().plusSeconds(3600),Instant.now().plusSeconds(7201),10));
        expectStatus(409,()->service.enroll(learner.getId(),campus,scheduled.id()));
        service.cancelCompetition(organizer.getId(),campus,scheduled.id(),"Reschedule later");
        assertThat(service.board(learner.getId(),campus,scheduled.id()).competition().status()).isEqualTo("CANCELLED");
    }
    @Test void concurrentEnrollmentCannotOverfillLastSlot() throws Exception {
        var second=user("second");accounts.saveAndFlush(new VirtualAccount(second,MarketRegion.INDIA,new BigDecimal("500000")));
        value(second,"500000",PricingStatus.LIVE,false);member(campus,second,"MEMBER");member(campus,learner,"MEMBER");
        var event=create(organizer,campus,2);service.enroll(organizer.getId(),campus,event.id());
        var result=race(()->service.enroll(learner.getId(),campus,event.id()),()->service.enroll(second.getId(),campus,event.id()));
        assertThat(result).containsExactlyInAnyOrder(200,409);
        assertThat(service.board(organizer.getId(),campus,event.id()).competition().participants()).isEqualTo(2);
    }
    @Test void concurrentApplicationsAndReviewsHaveOneWinner() throws Exception {
        assertThat(race(()->service.apply(learner.getId(),campus,"First"),()->service.apply(learner.getId(),other,"Other"))).containsExactlyInAnyOrder(200,409);
        var request=service.latest(learner.getId());var manager=request.institutionId().equals(campus)?organizer:outsider;
        assertThat(race(()->{service.review(manager.getId(),request.institutionId(),request.id(),new Review(true,"Verified"));return null;},
            ()->{service.review(manager.getId(),request.institutionId(),request.id(),new Review(false,"Not verified"));return null;})).containsExactlyInAnyOrder(200,409);
    }
    @Test void httpAuthorizationAndPrivacyUseCurrentMembershipAndNeverExposeAnotherBalance() throws Exception {
        member(campus,learner,"MEMBER");var event=create(organizer,campus,10);service.enroll(organizer.getId(),campus,event.id());
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String base="/api/v1/campus/institutions/"+campus;
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/campus/institutions/not-a-uuid").with(jwt().jwt(t->t.subject(learner.getId().toString())))).andExpect(status().isBadRequest());
        mvc.perform(post(base+"/competitions").with(jwt().jwt(t->t.subject(organizer.getId().toString())))
            .contentType("application/json").content("{")).andExpect(status().isBadRequest());
        mvc.perform(get(base+"/manage").with(jwt().jwt(t->t.subject(learner.getId().toString())))).andExpect(status().isNotFound());
        mvc.perform(get(base+"/competitions/"+event.id()).with(jwt().jwt(t->t.subject(learner.getId().toString()))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.yourBaselineValue").doesNotExist()).andExpect(jsonPath("$.standings[0].email").doesNotExist())
            .andExpect(jsonPath("$.standings[0].latestValue").doesNotExist());
        service.removeMember(organizer.getId(),campus,learner.getId(),"Removed");
        mvc.perform(get(base).with(jwt().jwt(t->t.subject(learner.getId().toString())))).andExpect(status().isNotFound());
        mvc.perform(post(base+"/competitions").with(jwt().jwt(t->t.subject(organizer.getId().toString())))
            .contentType("application/json").content("{\"title\":\"Test\",\"capacity\":1}")).andExpect(status().isBadRequest());
    }
    @Test void accountExportAndDeletionIncludeCampusRecordsAndAnonymizeAudit() {
        var request=service.apply(learner.getId(),campus,"Club");service.review(organizer.getId(),campus,request.id(),new Review(true,"Checked"));
        var event=create(organizer,campus,10);service.enroll(learner.getId(),campus,event.id());
        var exported=lifecycle.exportAccount(learner.getId());
        assertThat((List<?>)exported.get("campusJoinRequests")).hasSize(1);
        assertThat((List<?>)exported.get("campusCompetitionEntries")).hasSize(1);
        assertThat((List<?>)exported.get("campusModerationActions")).hasSize(3);
        users.deleteById(learner.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campus_join_request",Long.class)).isZero();
        assertThat(service.board(organizer.getId(),campus,event.id()).standings()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campus_audit WHERE actor_user_id=? OR target_user_id=?",Long.class,learner.getId(),learner.getId())).isZero();
    }
    private AppUser user(String name) { var u=new AppUser(name+"@example.test","hash",name);u.markEmailVerified();return users.saveAndFlush(u); }
    private UUID institution(String name) { var id=UUID.randomUUID();jdbc.update("INSERT INTO campus_institution(id,name,normalized_name,email_domain,verified_at) VALUES (?,?,?,?,now())",id,name,name.toLowerCase(),id+".edu");return id; }
    private void member(UUID institution,AppUser user,String role) { jdbc.update("INSERT INTO campus_membership(institution_id,user_id,member_role,joined_at) VALUES (?,?,?,now())",institution,user.getId(),role); }
    private Competition create(AppUser manager,UUID institution,int capacity) { return service.create(manager.getId(),institution,new NewCompetition("Campus challenge",null,Instant.now().plusSeconds(86400),capacity)); }
    private void value(AppUser user,String total,PricingStatus pricing,boolean hasHolding) {
        BigDecimal z=BigDecimal.ZERO;
        List<PortfolioPositionResponse> holdings=hasHolding?List.of(new PortfolioPositionResponse(UUID.randomUUID(),"NSE","TEST","Test","INR",1,0,1,z,z,z,z,z,z,pricing,Instant.now())):List.of();
        when(valuations.value(user.getId(),MarketRegion.INDIA)).thenReturn(new PortfolioResponse(MarketRegion.INDIA,"INR",new BigDecimal("500000"),z,z,z,z,z,z,z,new BigDecimal(total),z,pricing,Instant.now(),holdings));
    }
    private void expectStatus(int expected,Runnable action) { assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(expected)); }
    private List<Integer> race(Callable<?> first,Callable<?> second) throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->attempt(start,first));var b=pool.submit(()->attempt(start,second));start.countDown();
            return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
    private int attempt(CountDownLatch start,Callable<?> action) throws Exception { start.await();try { action.call();return 200; } catch(ResponseStatusException e) { return e.getStatusCode().value(); } }
}
