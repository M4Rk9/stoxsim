package com.stoxsim.competition.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.TokenService;
import com.stoxsim.common.error.UnauthorizedException;
import com.stoxsim.competition.api.CompetitionBoardResponse;
import com.stoxsim.competition.api.LeagueCreatedResponse;
import com.stoxsim.competition.api.LeagueDetailResponse;
import com.stoxsim.competition.api.LeagueInviteResponse;
import com.stoxsim.competition.api.LeagueSummaryResponse;
import com.stoxsim.competition.api.SeasonResponse;
import com.stoxsim.competition.api.StandingResponse;
import com.stoxsim.competition.domain.CompetitionEntry;
import com.stoxsim.competition.domain.CompetitionSeason;
import com.stoxsim.competition.domain.LeagueMember;
import com.stoxsim.competition.domain.LeagueMember.Role;
import com.stoxsim.competition.domain.PrivateLeague;
import com.stoxsim.competition.repository.CompetitionEntryRepository;
import com.stoxsim.competition.repository.CompetitionSeasonRepository;
import com.stoxsim.competition.repository.LeagueMemberRepository;
import com.stoxsim.competition.repository.PrivateLeagueRepository;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.api.PortfolioResponse;
import com.stoxsim.portfolio.service.PortfolioValuationService;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class CompetitionService {

    public static final String SCORING_VERSION = "standard-india-entry-return-v1";
    public static final String COMPARISON_NOTE = "Standings compare percentage change in each learner's standard India portfolio after that learner enrolled in the season. Join time and valuation freshness remain visible.";
    public static final String DISCLAIMER = "Competition standings use virtual portfolios for education and are not evidence of investing skill, future performance or investment advice.";

    private static final BigDecimal STANDARD_STARTING_CAPITAL = new BigDecimal("500000.0000");
    private static final int GLOBAL_LIMIT = 50;
    private static final int LEAGUE_LIMIT = 25;
    private static final int MAX_OWNED_LEAGUES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CompetitionSeasonRepository seasons;
    private final CompetitionEntryRepository entries;
    private final PrivateLeagueRepository leagues;
    private final LeagueMemberRepository members;
    private final AppUserRepository users;
    private final PortfolioValuationService valuations;
    private final TokenService tokens;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public CompetitionService(
        CompetitionSeasonRepository seasons,
        CompetitionEntryRepository entries,
        PrivateLeagueRepository leagues,
        LeagueMemberRepository members,
        AppUserRepository users,
        PortfolioValuationService valuations,
        TokenService tokens,
        Clock clock,
        MeterRegistry meterRegistry
    ) {
        this.seasons = seasons;
        this.entries = entries;
        this.leagues = leagues;
        this.members = members;
        this.users = users;
        this.valuations = valuations;
        this.tokens = tokens;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public CompetitionBoardResponse currentBoard(UUID userId) {
        requireUser(userId);
        CompetitionSeason season = currentSeason();
        CompetitionEntry ownEntry = entries.findForUpdate(season.getId(), userId)
            .map(entry -> refresh(entry, season))
            .orElse(null);
        return board(season, userId, ownEntry);
    }

    @Transactional
    public CompetitionBoardResponse enroll(UUID userId) {
        AppUser user = requireUser(userId);
        CompetitionSeason season = currentSeason();
        ensureOpen(season);
        CompetitionEntry entry = enrollEntry(season, user);
        meterRegistry.counter("stoxsim.competition.enrollment", "result", "active")
            .increment();
        return board(season, userId, entry);
    }

    @Transactional
    public List<LeagueSummaryResponse> leagues(UUID userId) {
        requireUser(userId);
        return leagues.findAllForMember(userId).stream()
            .map(league -> summary(league, userId))
            .toList();
    }

    @Transactional
    public LeagueCreatedResponse createLeague(UUID userId, String requestedName) {
        AppUser user = requireUser(userId);
        CompetitionSeason season = currentSeason();
        ensureOpen(season);
        requireEnrolledEntry(season, user);
        if (leagues.countByOwnerIdAndSeasonId(userId, season.getId()) >= MAX_OWNED_LEAGUES) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "You can own at most five private leagues per season"
            );
        }
        String inviteCode = inviteCode();
        Instant now = clock.instant();
        PrivateLeague league = leagues.save(new PrivateLeague(
            season,
            user,
            requestedName.trim(),
            tokens.hash(inviteCode),
            LEAGUE_LIMIT,
            now
        ));
        members.save(new LeagueMember(league, user, Role.OWNER, now));
        meterRegistry.counter("stoxsim.private_league.created").increment();
        return new LeagueCreatedResponse(
            detail(league, userId),
            inviteCode,
            "Share this code privately. StoxSim stores only its hash and cannot show it again. You can rotate it as the league owner."
        );
    }

    @Transactional
    public LeagueDetailResponse joinLeague(UUID userId, String requestedInviteCode) {
        AppUser user = requireUser(userId);
        String hash = tokens.hash(requestedInviteCode.trim());
        PrivateLeague league = leagues.findByInviteCodeHashForUpdate(hash)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Invite code is invalid or expired"
            ));
        ensureOpen(league.getSeason());
        if (members.existsByLeagueIdAndUserId(league.getId(), userId)) {
            return detail(league, userId);
        }
        if (members.countByLeagueId(league.getId()) >= league.getMaxMembers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This league is full");
        }
        requireEnrolledEntry(league.getSeason(), user);
        members.save(new LeagueMember(league, user, Role.MEMBER, clock.instant()));
        meterRegistry.counter("stoxsim.private_league.joined").increment();
        return detail(league, userId);
    }

    @Transactional
    public LeagueDetailResponse league(UUID userId, UUID leagueId) {
        PrivateLeague league = memberLeague(userId, leagueId);
        entries.findForUpdate(league.getSeason().getId(), userId)
            .ifPresent(entry -> refresh(entry, league.getSeason()));
        return detail(league, userId);
    }

    @Transactional
    public LeagueInviteResponse rotateInvite(UUID userId, UUID leagueId) {
        PrivateLeague league = leagues.findByIdForUpdate(leagueId)
            .orElseThrow(() -> notFound());
        ensureOwner(league, userId);
        ensureOpen(league.getSeason());
        String inviteCode = inviteCode();
        league.rotateInviteCode(tokens.hash(inviteCode), clock.instant());
        return new LeagueInviteResponse(
            inviteCode,
            "The previous invite code is now invalid. Share this replacement privately."
        );
    }

    @Transactional
    public void leaveLeague(UUID userId, UUID leagueId) {
        PrivateLeague league = leagues.findByIdForUpdate(leagueId)
            .orElseThrow(() -> notFound());
        LeagueMember member = members.findByLeagueIdAndUserId(leagueId, userId)
            .orElseThrow(() -> notFound());
        if (member.getRole() == Role.OWNER) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "League owners must delete the league instead of leaving"
            );
        }
        members.delete(member);
        meterRegistry.counter("stoxsim.private_league.left").increment();
    }

    @Transactional
    public void deleteLeague(UUID userId, UUID leagueId) {
        PrivateLeague league = leagues.findByIdForUpdate(leagueId)
            .orElseThrow(() -> notFound());
        ensureOwner(league, userId);
        leagues.delete(league);
        meterRegistry.counter("stoxsim.private_league.deleted").increment();
    }

    private CompetitionBoardResponse board(
        CompetitionSeason season,
        UUID userId,
        CompetitionEntry ownEntry
    ) {
        List<CompetitionEntry> ranked = entries
            .findAllBySeasonIdOrderByReturnPercentDescJoinedAtAsc(
                season.getId(),
                PageRequest.of(0, GLOBAL_LIMIT)
            );
        Integer ownRank = ownEntry == null
            ? null
            : Math.toIntExact(1 + entries.countBySeasonIdAndReturnPercentGreaterThan(
                season.getId(),
                ownEntry.getReturnPercent()
            ));
        return new CompetitionBoardResponse(
            season(season),
            ownEntry != null,
            entries.countBySeasonId(season.getId()),
            ownRank,
            ownEntry == null ? null : ownEntry.getBaselineValue(),
            ownEntry == null ? null : ownEntry.getLatestValue(),
            standings(ranked, userId),
            COMPARISON_NOTE,
            DISCLAIMER
        );
    }

    private LeagueDetailResponse detail(PrivateLeague league, UUID userId) {
        if (!members.existsByLeagueIdAndUserId(league.getId(), userId)) {
            throw notFound();
        }
        List<CompetitionEntry> ranked = entries.findLeagueStandings(
            league.getSeason().getId(),
            league.getId(),
            PageRequest.of(0, league.getMaxMembers())
        );
        return new LeagueDetailResponse(
            summary(league, userId),
            season(league.getSeason()),
            standings(ranked, userId),
            COMPARISON_NOTE,
            DISCLAIMER
        );
    }

    private LeagueSummaryResponse summary(PrivateLeague league, UUID userId) {
        return new LeagueSummaryResponse(
            league.getId(),
            league.getName(),
            league.getSeason().getCode(),
            league.getOwner().getDisplayName(),
            league.getOwner().getId().equals(userId),
            members.countByLeagueId(league.getId()),
            league.getMaxMembers(),
            league.getCreatedAt()
        );
    }

    private List<StandingResponse> standings(
        List<CompetitionEntry> entries,
        UUID currentUserId
    ) {
        List<StandingResponse> standings = new ArrayList<>();
        BigDecimal previous = null;
        int rank = 0;
        for (int index = 0; index < entries.size(); index++) {
            CompetitionEntry entry = entries.get(index);
            if (previous == null || previous.compareTo(entry.getReturnPercent()) != 0) {
                rank = index + 1;
                previous = entry.getReturnPercent();
            }
            standings.add(new StandingResponse(
                rank,
                entry.getUser().getDisplayName(),
                entry.getReturnPercent(),
                entry.getDataStatus().name(),
                entry.getJoinedAt(),
                entry.getValuedAt(),
                entry.getUser().getId().equals(currentUserId)
            ));
        }
        return List.copyOf(standings);
    }

    private CompetitionEntry enrollEntry(CompetitionSeason season, AppUser user) {
        CompetitionEntry existing = entries.findForUpdate(season.getId(), user.getId())
            .orElse(null);
        if (existing != null) {
            return refresh(existing, season);
        }
        PortfolioResponse portfolio = competitivePortfolio(user.getId(), true);
        Instant now = clock.instant();
        entries.ensureEntry(
            season.getId(),
            user.getId(),
            portfolio.totalAccountValue(),
            portfolio.dataStatus().name(),
            now
        );
        return entries.findForUpdate(season.getId(), user.getId())
            .orElseThrow(() -> new IllegalStateException("Competition entry could not be initialized"));
    }

    private CompetitionEntry requireEnrolledEntry(
        CompetitionSeason season,
        AppUser user
    ) {
        return entries.findForUpdate(season.getId(), user.getId())
            .map(entry -> refresh(entry, season))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Join the standard leaderboard before creating or joining a private league"
            ));
    }

    private CompetitionEntry refresh(
        CompetitionEntry entry,
        CompetitionSeason season
    ) {
        if (!open(season)) {
            return entry;
        }
        PortfolioResponse portfolio = competitivePortfolio(entry.getUser().getId(), false);
        if (portfolio.dataStatus() == PricingStatus.UNAVAILABLE
            && !portfolio.holdings().isEmpty()) {
            meterRegistry.counter("stoxsim.competition.valuation", "result", "skipped")
                .increment();
            return entry;
        }
        entry.refresh(
            portfolio.totalAccountValue(),
            portfolio.dataStatus(),
            portfolio.valuedAt()
        );
        meterRegistry.counter("stoxsim.competition.valuation", "result", "updated")
            .increment();
        return entry;
    }

    private PortfolioResponse competitivePortfolio(UUID userId, boolean enrollment) {
        PortfolioResponse portfolio = valuations.value(userId, MarketRegion.INDIA);
        if (portfolio.startingCapital().compareTo(STANDARD_STARTING_CAPITAL) != 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only the standard ₹5 lakh India portfolio can enter competitions"
            );
        }
        if (enrollment
            && portfolio.dataStatus() == PricingStatus.UNAVAILABLE
            && !portfolio.holdings().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Portfolio prices are unavailable; try enrolling after pricing recovers"
            );
        }
        return portfolio;
    }

    private CompetitionSeason currentSeason() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        int quarter = ((today.getMonthValue() - 1) / 3) + 1;
        int startMonth = ((quarter - 1) * 3) + 1;
        Instant startsAt = LocalDate.of(today.getYear(), startMonth, 1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
        Instant endsAt = LocalDate.of(today.getYear(), startMonth, 1)
            .plusMonths(3)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
        String code = today.getYear() + "-Q" + quarter;
        seasons.ensureSeason(
            code,
            today.getYear() + " Q" + quarter + " Learning Season",
            startsAt,
            endsAt,
            now
        );
        return seasons.findByCode(code)
            .orElseThrow(() -> new IllegalStateException("Competition season could not be initialized"));
    }

    private SeasonResponse season(CompetitionSeason season) {
        return new SeasonResponse(
            season.getCode(),
            season.getTitle(),
            season.getStartsAt(),
            season.getEndsAt(),
            open(season),
            SCORING_VERSION,
            MarketRegion.INDIA.name(),
            MarketRegion.INDIA.currency(),
            STANDARD_STARTING_CAPITAL
        );
    }

    private PrivateLeague memberLeague(UUID userId, UUID leagueId) {
        if (!members.existsByLeagueIdAndUserId(leagueId, userId)) {
            throw notFound();
        }
        return leagues.findById(leagueId).orElseThrow(() -> notFound());
    }

    private void ensureOwner(PrivateLeague league, UUID userId) {
        if (!league.getOwner().getId().equals(userId)) {
            throw notFound();
        }
    }

    private void ensureOpen(CompetitionSeason season) {
        if (!open(season)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This season is closed");
        }
    }

    private boolean open(CompetitionSeason season) {
        Instant now = clock.instant();
        return !now.isBefore(season.getStartsAt()) && now.isBefore(season.getEndsAt());
    }

    private AppUser requireUser(UUID userId) {
        return users.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found");
    }

    private String inviteCode() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return "STX-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
