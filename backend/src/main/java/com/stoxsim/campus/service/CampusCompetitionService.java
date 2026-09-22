package com.stoxsim.campus.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.AccountLifecycleService;
import com.stoxsim.campus.api.CampusCompetitionDtos.*;
import com.stoxsim.campus.repository.CampusCompetitionRepository;
import com.stoxsim.common.error.UnauthorizedException;
import com.stoxsim.competition.api.StandingResponse;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.api.PortfolioResponse;
import com.stoxsim.portfolio.service.PortfolioValuationService;
import static com.stoxsim.campus.repository.CampusCompetitionRepository.instant;

@Service
@Transactional(timeout=15)
public class CampusCompetitionService {
    public static final String NOTE = "Campus standings show percentage change since each learner enrolled, using only the standard India portfolio. Only your own score refreshes when you open the board; other rows show their last valuation. After the end, last observed values remain frozen, not official closing-price results. Participation is educational, not investment advice.";
    private final CampusCompetitionRepository repo;
    private final AppUserRepository users;
    private final VirtualAccountRepository accounts;
    private final PortfolioValuationService valuations;
    private final AccountLifecycleService lifecycle;
    private final Clock clock;
    public CampusCompetitionService(CampusCompetitionRepository repo, AppUserRepository users,
        VirtualAccountRepository accounts, PortfolioValuationService valuations, AccountLifecycleService lifecycle, Clock clock) {
        this.repo=repo;this.users=users;this.accounts=accounts;this.valuations=valuations;this.lifecycle=lifecycle;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<Institution> directory(UUID actor,String query) {
        var user=user(actor,false);
        if(query==null || query.length()>160) throw bad("Search must be at most 160 characters");
        return repo.directory(query.trim(),user.isPlatformAdmin());
    }
    @Transactional(readOnly=true)
    public JoinRequest latest(UUID actor) { user(actor,false);return repo.latest(actor); }
    @Transactional(readOnly=true)
    public Workspace workspace(UUID actor,UUID institution) {
        var campus=campus(institution,false);var role=access(actor,institution,false);
        return new Workspace(campus,role,repo.competitions(institution,actor,clock.instant()));
    }
    @Transactional(readOnly=true)
    public Management management(UUID actor,UUID institution) {
        campus(institution,false);access(actor,institution,true);
        return new Management(repo.members(institution),repo.pending(institution),repo.audit(institution));
    }
    public JoinRequest apply(UUID actor,UUID institution,String note) {
        active(campus(institution,true));var user=user(actor,true);verified(user);
        if(repo.membershipInstitution(actor)!=null) throw conflict("You already belong to an institution");
        var latest=repo.latest(actor);
        if(latest!=null && latest.status().equals("PENDING")) {
            if(latest.institutionId().equals(institution)) return latest;
            throw conflict("Cancel your pending membership request before applying elsewhere");
        }
        if(repo.pending(institution).size()>=200) throw conflict("The membership queue is full; try again after review");
        var id=UUID.randomUUID();
        repo.jdbc().update("INSERT INTO campus_join_request(id,institution_id,user_id,note,submitted_at) VALUES (?,?,?,?,?)",
            id,institution,actor,text(note,300),ts());
        audit(institution,actor,actor,"MEMBERSHIP_REQUESTED");return repo.request(id);
    }
    public void cancelRequest(UUID actor,UUID requestId) {
        var initial=repo.request(requestId);if(initial==null || !initial.userId().equals(actor)) throw missing();
        campus(initial.institutionId(),true);user(actor,true);
        var request=repo.request(requestId);pending(request);
        repo.jdbc().update("UPDATE campus_join_request SET status='CANCELLED',reviewed_at=? WHERE id=?",ts(),requestId);
        audit(request.institutionId(),actor,actor,"MEMBERSHIP_REQUEST_CANCELLED");
    }
    public void review(UUID actor,UUID institution,UUID requestId,Review decision) {
        var campus=campus(institution,true);access(actor,institution,true);
        var request=repo.request(requestId);if(request==null || !request.institutionId().equals(institution)) throw missing();
        user(request.userId(),true);request=repo.request(requestId);pending(request);
        String note=text(decision.note(),500);
        if(decision.approve()) {
            active(campus);verified(user(request.userId(),false));
            if(repo.membershipInstitution(request.userId())!=null) throw conflict("This learner already belongs to an institution");
            if(repo.members(institution).size()>=200) throw conflict("This campus has reached its 200-member limit");
            repo.jdbc().update("INSERT INTO campus_membership(institution_id,user_id,member_role,joined_at) VALUES (?,?,'MEMBER',?)",institution,request.userId(),ts());
        }
        repo.jdbc().update("UPDATE campus_join_request SET status=?,review_note=?,reviewed_by=?,reviewed_at=? WHERE id=?",
            decision.approve()?"APPROVED":"REJECTED",note,actor,ts(),requestId);
        audit(institution,actor,request.userId(),decision.approve()?"MEMBERSHIP_APPROVED":"MEMBERSHIP_REJECTED");
    }
    public void changeRole(UUID actor,UUID institution,UUID target,String role) {
        active(campus(institution,true));access(actor,institution,true);user(target,true);
        String current=repo.role(institution,target);if(current==null) throw missing();
        if(!List.of("ORGANIZER","MEMBER").contains(role)) throw bad("Unknown campus role");
        verified(user(target,false));
        if(current.equals("ORGANIZER") && role.equals("MEMBER")) preserveOrganizer(institution);
        if(current.equals(role)) return;
        repo.jdbc().update("UPDATE campus_membership SET member_role=? WHERE institution_id=? AND user_id=?",role,institution,target);
        audit(institution,actor,target,"ROLE_CHANGED_TO_"+role);
    }
    public void removeMember(UUID actor,UUID institution,UUID target,String note) {
        campus(institution,true);
        if(actor.equals(target)) access(actor,institution,false);else access(actor,institution,true);
        user(target,true);String role=repo.role(institution,target);if(role==null) throw missing();
        text(note,500);if(role.equals("ORGANIZER")) preserveOrganizer(institution);
        withdrawAll(institution,target);
        repo.jdbc().update("DELETE FROM campus_membership WHERE institution_id=? AND user_id=?",institution,target);
        audit(institution,actor,target,actor.equals(target)?"MEMBER_LEFT":"MEMBER_REMOVED");
        lifecycle.audit(actor,"CAMPUS_MEMBER_REMOVED",institution+":"+target+":"+note.trim());
    }
    public void recoverOrganizer(UUID actor,UUID institution,Organizer request) {
        campus(institution,true);admin(actor);
        var target=users.findByEmailIgnoreCase(request.email().trim()).orElseThrow(()->conflict("A verified StoxSim account with this email is required"));
        target=user(target.getId(),true);verified(target);text(request.note(),500);
        var existing=repo.membershipInstitution(target.getId());
        if(existing!=null && !existing.equals(institution)) throw conflict("This account belongs to another institution");
        if(existing==null && repo.members(institution).size()>=200) throw conflict("This campus is full");
        repo.jdbc().update("""
            INSERT INTO campus_membership(institution_id,user_id,member_role,joined_at) VALUES (?,?,'ORGANIZER',?)
            ON CONFLICT (user_id) DO UPDATE SET member_role='ORGANIZER'
            """,institution,target.getId(),ts());
        repo.jdbc().update("UPDATE campus_join_request SET status='CANCELLED',reviewed_at=? WHERE user_id=? AND status='PENDING'",ts(),target.getId());
        audit(institution,actor,target.getId(),"ORGANIZER_RECOVERED");
        lifecycle.audit(actor,"CAMPUS_ORGANIZER_RECOVERED",institution+":"+target.getId()+":"+request.note().trim());
    }
    public void suspend(UUID actor,UUID institution,Suspension request) {
        campus(institution,true);admin(actor);text(request.note(),500);
        repo.jdbc().update("UPDATE campus_institution SET suspended=? WHERE id=?",request.suspended(),institution);
        audit(institution,actor,null,request.suspended()?"INSTITUTION_SUSPENDED":"INSTITUTION_RESTORED");
        lifecycle.audit(actor,"CAMPUS_MODERATED",institution+":"+request.note().trim());
    }
    public Competition create(UUID actor,UUID institution,NewCompetition request) {
        active(campus(institution,true));access(actor,institution,true);verified(user(actor,false));
        var now=clock.instant();var start=request.startsAt()==null?now:request.startsAt();var end=request.endsAt();
        validateSchedule(start,end,now,request.capacity());
        Long ongoing=repo.jdbc().queryForObject("SELECT count(*) FROM campus_competition WHERE institution_id=? AND NOT cancelled AND ends_at>?",Long.class,institution,Timestamp.from(now));
        if(ongoing>=10) throw conflict("A campus may have at most ten upcoming or active competitions");
        var id=UUID.randomUUID();
        repo.jdbc().update("INSERT INTO campus_competition(id,institution_id,title,starts_at,ends_at,capacity,created_by,created_at) VALUES (?,?,?,?,?,?,?,?)",
            id,institution,title(request.title()),Timestamp.from(start),Timestamp.from(end),request.capacity(),actor,Timestamp.from(now));
        audit(institution,actor,null,"COMPETITION_CREATED",id);return repo.competition(id,actor,now);
    }
    public void cancelCompetition(UUID actor,UUID institution,UUID competition,String note) {
        campus(institution,true);access(actor,institution,true);var item=competition(competition,institution,actor);
        if(item.status().equals("ENDED")) throw conflict("An ended competition cannot be cancelled");
        if(item.status().equals("CANCELLED")) return;
        repo.jdbc().update("UPDATE campus_competition SET cancelled=true,cancellation_note=? WHERE id=?",text(note,500),competition);
        audit(institution,actor,null,"COMPETITION_CANCELLED",competition);
    }
    public Board enroll(UUID actor,UUID institution,UUID competition) {
        active(campus(institution,true));access(actor,institution,false);verified(user(actor,true));
        if(repo.role(institution,actor)==null) throw missing(); // admin visibility alone is not membership
        var item=competition(competition,institution,actor);open(item);
        if(item.withdrawn()) throw conflict("Withdrawal is final for this competition; you cannot reset your baseline");
        if(!item.enrolled()) {
            if(item.participants()>=item.capacity()) throw conflict("This competition is full");
            var account=accounts.findForUpdate(actor,MarketRegion.INDIA).orElseThrow(()->conflict("A standard India portfolio is required"));
            var portfolio=portfolio(actor);
            if(unavailable(portfolio)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Portfolio prices are unavailable; try again later");
            if(portfolio.totalAccountValue().signum()<=0) throw conflict("A positive portfolio value is required");
            var now=clock.instant();
            // Price lookup may straddle the end; never admit an entry after closure.
            open(competition(competition,institution,actor));
            repo.jdbc().update("""
                INSERT INTO campus_competition_entry(competition_id,user_id,account_id,baseline_value,latest_value,data_status,joined_at,valued_at)
                VALUES (?,?,?,?,?,?,?,?)
                """,competition,actor,account.getId(),portfolio.totalAccountValue(),portfolio.totalAccountValue(),portfolio.dataStatus().name(),Timestamp.from(now),Timestamp.from(portfolio.valuedAt()));
            audit(institution,actor,actor,"COMPETITION_ENROLLED",competition);
        }
        return boardData(actor,competition(competition,institution,actor),false);
    }
    public void withdraw(UUID actor,UUID institution,UUID competition) {
        campus(institution,true);access(actor,institution,false);competition(competition,institution,actor);
        int changed=repo.jdbc().update("UPDATE campus_competition_entry SET withdrawn_at=? WHERE competition_id=? AND user_id=? AND withdrawn_at IS NULL",ts(),competition,actor);
        if(changed>0) audit(institution,actor,actor,"COMPETITION_WITHDRAWN",competition);
    }
    public Board board(UUID actor,UUID institution,UUID competition) {
        var campus=campus(institution,true);access(actor,institution,false);
        var item=competition(competition,institution,actor);boolean unavailable=false;
        if(!campus.suspended() && item.status().equals("OPEN") && item.enrolled()) {
            // Same account lock as trading keeps the valuation internally consistent.
            accounts.findForUpdate(actor,MarketRegion.INDIA).orElseThrow(()->conflict("Standard account not found"));
            var portfolio=portfolio(actor);unavailable=unavailable(portfolio);
            if(!unavailable && competition(competition,institution,actor).status().equals("OPEN")) {
                BigDecimal baseline=repo.jdbc().queryForObject("SELECT baseline_value FROM campus_competition_entry WHERE competition_id=? AND user_id=?",BigDecimal.class,competition,actor);
                var change=portfolio.totalAccountValue().subtract(baseline).multiply(BigDecimal.valueOf(100)).divide(baseline,4,RoundingMode.HALF_UP);
                repo.jdbc().update("UPDATE campus_competition_entry SET latest_value=?,return_percent=?,data_status=?,valued_at=? WHERE competition_id=? AND user_id=?",
                    portfolio.totalAccountValue(),change,portfolio.dataStatus().name(),Timestamp.from(portfolio.valuedAt()),competition,actor);
            }
        }
        return boardData(actor,competition(competition,institution,actor),unavailable);
    }
    private Board boardData(UUID actor,Competition item,boolean unavailable) {
        var standings=repo.jdbc().query("""
            SELECT rank() OVER (ORDER BY e.return_percent DESC) AS rank,u.display_name,e.*
            FROM campus_competition_entry e JOIN app_user u ON u.id=e.user_id
            JOIN campus_membership m ON m.user_id=e.user_id AND m.institution_id=?
            WHERE e.competition_id=? AND e.withdrawn_at IS NULL ORDER BY e.return_percent DESC,e.joined_at,e.user_id LIMIT 200
            """,(rs,row)->new StandingResponse(rs.getInt("rank"),rs.getString("display_name"),rs.getBigDecimal("return_percent"),
                rs.getString("data_status"),instant(rs,"joined_at"),instant(rs,"valued_at"),actor.equals(rs.getObject("user_id",UUID.class))),item.institutionId(),item.id());
        var own=repo.jdbc().query("SELECT baseline_value,latest_value FROM campus_competition_entry WHERE competition_id=? AND user_id=? AND withdrawn_at IS NULL",
            (rs,row)->new BigDecimal[]{rs.getBigDecimal(1),rs.getBigDecimal(2)},item.id(),actor);
        return new Board(item,standings,own.isEmpty()?null:own.getFirst()[0],own.isEmpty()?null:own.getFirst()[1],unavailable,NOTE);
    }
    private PortfolioResponse portfolio(UUID actor) {
        var result=valuations.value(actor,MarketRegion.INDIA);
        if(result.marketRegion()!=MarketRegion.INDIA || result.startingCapital().compareTo(new BigDecimal("500000"))!=0)
            throw conflict("Only the standard ₹5 lakh India portfolio may compete");
        return result;
    }
    private boolean unavailable(PortfolioResponse portfolio) { return portfolio.dataStatus()==PricingStatus.UNAVAILABLE && !portfolio.holdings().isEmpty(); }
    public static void validateSchedule(Instant start,Instant end,Instant now,int capacity) {
        if(start==null || end==null || start.isBefore(now) || start.isAfter(now.plus(Duration.ofDays(90)))
            || end.isBefore(start.plus(Duration.ofHours(1))) || end.isAfter(start.plus(Duration.ofDays(90))) || capacity<2 || capacity>200)
            throw bad("Start within the next 90 days; duration 1 hour–90 days; capacity 2–200");
    }
    private void withdrawAll(UUID institution,UUID target) {
        repo.jdbc().update("""
            UPDATE campus_competition_entry SET withdrawn_at=? WHERE user_id=? AND withdrawn_at IS NULL
            AND competition_id IN (SELECT id FROM campus_competition WHERE institution_id=?)
            """,ts(),target,institution);
    }
    private void preserveOrganizer(UUID institution) {
        Long count=repo.jdbc().queryForObject("SELECT count(*) FROM campus_membership WHERE institution_id=? AND member_role='ORGANIZER'",Long.class,institution);
        if(count<=1) throw conflict("Assign another organizer before removing or demoting the last organizer");
    }
    private String access(UUID actor,UUID institution,boolean manage) {
        var user=user(actor,false);if(user.isPlatformAdmin()) return "ADMIN";
        var role=repo.role(institution,actor);
        if(role==null || (manage && !role.equals("ORGANIZER"))) throw missing();return role;
    }
    private AppUser admin(UUID actor) { var user=user(actor,false);if(!user.isPlatformAdmin()) throw missing();return user; }
    private AppUser user(UUID id,boolean lock) { return (lock?users.findByIdForUpdate(id):users.findById(id)).orElseThrow(()->new UnauthorizedException("User no longer exists")); }
    private void verified(AppUser user) { if(!user.isEmailVerified()) throw conflict("Verify your StoxSim email first"); }
    private Institution campus(UUID id,boolean lock) { var campus=repo.institution(id,lock);if(campus==null) throw missing();return campus; }
    private void active(Institution campus) { if(campus.suspended()) throw conflict("This institution is suspended; contact StoxSim support"); }
    private Competition competition(UUID id,UUID institution,UUID actor) {
        var result=repo.competition(id,actor,clock.instant());if(result==null || !result.institutionId().equals(institution)) throw missing();return result;
    }
    private void open(Competition item) { if(!item.status().equals("OPEN")) throw conflict("Enrollment is available only while this competition is open"); }
    private void pending(JoinRequest request) { if(request==null || !request.status().equals("PENDING")) throw conflict("This membership request has already been reviewed or cancelled"); }
    private void audit(UUID institution,UUID actor,UUID target,String action) { repo.audit(institution,actor,target,action,null,clock.instant()); }
    private void audit(UUID institution,UUID actor,UUID target,String action,UUID competition) { repo.audit(institution,actor,target,action,competition,clock.instant()); }
    private Timestamp ts() { return Timestamp.from(clock.instant()); }
    private static String text(String text,int max) { if(text==null || text.isBlank() || text.trim().length()>max) throw bad("A note of at most "+max+" characters is required");return text.trim(); }
    private static String title(String value) { String s=text(value,100);if(s.length()<3) throw bad("Title needs at least three characters");return s; }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Campus resource not found"); }
}
