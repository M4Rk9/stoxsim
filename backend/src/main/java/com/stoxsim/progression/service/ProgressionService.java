package com.stoxsim.progression.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.repository.HoldingRepository;
import com.stoxsim.progression.api.AchievementResponse;
import com.stoxsim.progression.api.ChallengeResponse;
import com.stoxsim.progression.api.MissionResponse;
import com.stoxsim.progression.api.ProgressionResponse;
import com.stoxsim.progression.domain.AchievementUnlock;
import com.stoxsim.progression.domain.LearnerProgression;
import com.stoxsim.progression.domain.MissionCompletion;
import com.stoxsim.progression.repository.AchievementUnlockRepository;
import com.stoxsim.progression.repository.LearnerProgressionRepository;
import com.stoxsim.progression.repository.MissionCompletionRepository;
import com.stoxsim.report.domain.WeeklyReportPreference;
import com.stoxsim.report.repository.WeeklyReportPreferenceRepository;
import com.stoxsim.trade.repository.TradeRepository;
import com.stoxsim.watchlist.repository.WatchlistItemRepository;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class ProgressionService {

    public static final String VERSION = "learning-progression-v1";
    public static final String DISCLAIMER = "XP, levels, streaks and achievements reflect educational activity in this simulator. They do not measure investing skill, predict returns or provide investment advice.";

    private static final List<MissionDefinition> MISSIONS = List.of(
        new MissionDefinition(
            "ONBOARDING_COMPLETE",
            "FOUNDATIONS",
            "Complete the learning introduction",
            "Finish the guided explanation of StoxSim's two simulated markets and data labels.",
            50,
            1,
            ProgressMetrics::onboardingComplete
        ),
        new MissionDefinition(
            "WATCHLIST_READY",
            "FOUNDATIONS",
            "Build your first watchlist",
            "Save one instrument so you can observe it before deciding how to practise.",
            40,
            1,
            metrics -> metrics.watchlistItems() > 0
        ),
        new MissionDefinition(
            "FIRST_ORDER",
            "FOUNDATIONS",
            "Place your first paper order",
            "Use the order ticket once with virtual capital and review its status.",
            50,
            1,
            ProgressMetrics::firstOrderPlaced
        ),
        new MissionDefinition(
            "FIRST_EXECUTION",
            "PRACTICE",
            "Complete your first simulated trade",
            "Have one paper order execute and inspect the resulting holding and simulated charges.",
            75,
            1,
            metrics -> metrics.totalTrades() > 0
        ),
        new MissionDefinition(
            "MULTI_MARKET_EXPLORER",
            "PRACTICE",
            "Explore both market accounts",
            "Complete at least one simulated trade in both the India and United States accounts.",
            100,
            2,
            metrics -> metrics.tradedMarkets() >= 2
        ),
        new MissionDefinition(
            "DIVERSIFICATION_FOUNDATIONS",
            "PRACTICE",
            "Build a three-position portfolio",
            "Hold three different instruments across your simulated accounts and compare their weights.",
            125,
            3,
            metrics -> metrics.activeHoldings() >= 3
        ),
        new MissionDefinition(
            "THREE_DAY_STREAK",
            "CONSISTENCY",
            "Check in for three learning days",
            "Open the progression path and record one learning check-in on three consecutive local dates.",
            100,
            3,
            metrics -> metrics.effectiveStreak() >= 3
        )
    );

    private static final List<ChallengeDefinition> CHALLENGES = List.of(
        new ChallengeDefinition(
            "FOUNDATIONS",
            "Learning foundations",
            "Set up a deliberate, observation-first paper-trading workflow."
        ),
        new ChallengeDefinition(
            "PRACTICE",
            "Portfolio practice",
            "Use virtual accounts to learn execution, market separation and allocation."
        ),
        new ChallengeDefinition(
            "CONSISTENCY",
            "Learning consistency",
            "Return regularly without tying progress to profit or trading volume."
        )
    );

    private static final List<LevelDefinition> LEVELS = List.of(
        new LevelDefinition(1, "Starter", 0),
        new LevelDefinition(2, "Explorer", 100),
        new LevelDefinition(3, "Analyst", 250),
        new LevelDefinition(4, "Strategist", 450),
        new LevelDefinition(5, "Scholar", 700)
    );

    private static final List<AchievementDefinition> ACHIEVEMENTS = List.of(
        new AchievementDefinition(
            "FIRST_MISSION",
            "First step",
            "Complete any learning mission.",
            state -> state.completedMissions() >= 1
        ),
        new AchievementDefinition(
            "LEVEL_2",
            "Market explorer",
            "Reach level 2 through educational milestones.",
            state -> state.totalXp() >= 100
        ),
        new AchievementDefinition(
            "LEVEL_3",
            "Developing analyst",
            "Reach level 3 through educational milestones.",
            state -> state.totalXp() >= 250
        ),
        new AchievementDefinition(
            "LEVEL_4",
            "Portfolio strategist",
            "Reach level 4 through educational milestones.",
            state -> state.totalXp() >= 450
        ),
        new AchievementDefinition(
            "CONSISTENT_LEARNER",
            "Consistent learner",
            "Complete the three-day learning streak mission.",
            state -> state.completedCodes().contains("THREE_DAY_STREAK")
        ),
        new AchievementDefinition(
            "PATH_COMPLETE",
            "Pathfinder",
            "Complete every mission in learning-progression-v1.",
            state -> state.completedMissions() == MISSIONS.size()
        )
    );

    private final AppUserRepository users;
    private final LearnerProgressionRepository progressions;
    private final MissionCompletionRepository completions;
    private final AchievementUnlockRepository achievements;
    private final TradeRepository trades;
    private final HoldingRepository holdings;
    private final WatchlistItemRepository watchlistItems;
    private final WeeklyReportPreferenceRepository reportPreferences;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public ProgressionService(
        AppUserRepository users,
        LearnerProgressionRepository progressions,
        MissionCompletionRepository completions,
        AchievementUnlockRepository achievements,
        TradeRepository trades,
        HoldingRepository holdings,
        WatchlistItemRepository watchlistItems,
        WeeklyReportPreferenceRepository reportPreferences,
        Clock clock,
        MeterRegistry meterRegistry
    ) {
        this.users = users;
        this.progressions = progressions;
        this.completions = completions;
        this.achievements = achievements;
        this.trades = trades;
        this.holdings = holdings;
        this.watchlistItems = watchlistItems;
        this.reportPreferences = reportPreferences;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public ProgressionResponse state(UUID userId) {
        AppUser user = requireUser(userId);
        ZoneId zoneId = zoneId(userId);
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        LearnerProgression progression = lockedProgression(userId);
        return reconcile(user, progression, zoneId, today);
    }

    @Transactional
    public ProgressionResponse checkIn(UUID userId) {
        AppUser user = requireUser(userId);
        ZoneId zoneId = zoneId(userId);
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        LearnerProgression progression = lockedProgression(userId);
        if (progression.checkIn(today, clock.instant())) {
            meterRegistry.counter("stoxsim.progression.check_in", "result", "recorded")
                .increment();
        } else {
            meterRegistry.counter("stoxsim.progression.check_in", "result", "duplicate")
                .increment();
        }
        return reconcile(user, progression, zoneId, today);
    }

    private ProgressionResponse reconcile(
        AppUser user,
        LearnerProgression progression,
        ZoneId zoneId,
        LocalDate today
    ) {
        UUID userId = user.getId();
        ProgressMetrics metrics = metrics(user, progression, today);
        Map<String, MissionCompletion> completed = completions
            .findAllByUserIdOrderByCompletedAt(userId)
            .stream()
            .collect(Collectors.toMap(
                MissionCompletion::getMissionCode,
                completion -> completion,
                (first, duplicate) -> first,
                LinkedHashMap::new
            ));
        Instant now = clock.instant();
        for (MissionDefinition mission : MISSIONS) {
            if (!completed.containsKey(mission.code()) && mission.complete().test(metrics)) {
                MissionCompletion completion = completions.save(new MissionCompletion(
                    userId,
                    mission.code(),
                    mission.xp(),
                    now
                ));
                completed.put(mission.code(), completion);
                progression.awardXp(mission.xp(), now);
                meterRegistry.counter(
                    "stoxsim.progression.mission_completed",
                    "mission",
                    mission.code()
                ).increment();
            }
        }

        Map<String, AchievementUnlock> unlocked = achievements
            .findAllByUserIdOrderByUnlockedAt(userId)
            .stream()
            .collect(Collectors.toMap(
                AchievementUnlock::getAchievementCode,
                achievement -> achievement,
                (first, duplicate) -> first,
                LinkedHashMap::new
            ));
        AchievementState achievementState = new AchievementState(
            progression.getTotalXp(),
            completed.size(),
            completed.keySet()
        );
        for (AchievementDefinition achievement : ACHIEVEMENTS) {
            if (!unlocked.containsKey(achievement.code())
                && achievement.unlocked().test(achievementState)) {
                AchievementUnlock unlock = achievements.save(new AchievementUnlock(
                    userId,
                    achievement.code(),
                    now
                ));
                unlocked.put(achievement.code(), unlock);
                meterRegistry.counter(
                    "stoxsim.progression.achievement_unlocked",
                    "achievement",
                    achievement.code()
                ).increment();
            }
        }

        LevelDefinition level = level(progression.getTotalXp());
        Integer nextLevelXp = level.level() == LEVELS.getLast().level()
            ? null
            : LEVELS.get(level.level()).floorXp();
        return new ProgressionResponse(
            VERSION,
            progression.getTotalXp(),
            level.level(),
            level.name(),
            level.floorXp(),
            nextLevelXp,
            metrics.effectiveStreak(),
            progression.getLongestStreak(),
            progression.getLastCheckInDate(),
            zoneId.getId(),
            today.equals(progression.getLastCheckInDate()),
            challengeResponses(metrics, completed),
            achievementResponses(unlocked),
            DISCLAIMER
        );
    }

    private ProgressMetrics metrics(
        AppUser user,
        LearnerProgression progression,
        LocalDate today
    ) {
        long indiaTrades = trades.countByUserIdAndMarketRegion(
            user.getId(),
            MarketRegion.INDIA
        );
        long unitedStatesTrades = trades.countByUserIdAndMarketRegion(
            user.getId(),
            MarketRegion.UNITED_STATES
        );
        int streak = effectiveStreak(progression, today);
        return new ProgressMetrics(
            user.getOnboardingIntroCompletedAt() != null,
            user.getFirstOrderPlacedAt() != null,
            watchlistItems.countByUserId(user.getId()),
            indiaTrades,
            unitedStatesTrades,
            holdings.countActiveByUserId(user.getId()),
            streak
        );
    }

    private List<ChallengeResponse> challengeResponses(
        ProgressMetrics metrics,
        Map<String, MissionCompletion> completed
    ) {
        List<ChallengeResponse> responses = new ArrayList<>();
        for (ChallengeDefinition challenge : CHALLENGES) {
            List<MissionResponse> missions = MISSIONS.stream()
                .filter(mission -> challenge.code().equals(mission.challengeCode()))
                .map(mission -> missionResponse(mission, metrics, completed.get(mission.code())))
                .toList();
            responses.add(new ChallengeResponse(
                challenge.code(),
                challenge.title(),
                challenge.description(),
                (int) missions.stream().filter(MissionResponse::completed).count(),
                missions.size(),
                missions
            ));
        }
        return List.copyOf(responses);
    }

    private MissionResponse missionResponse(
        MissionDefinition mission,
        ProgressMetrics metrics,
        MissionCompletion completion
    ) {
        int progress = switch (mission.code()) {
            case "ONBOARDING_COMPLETE" -> metrics.onboardingComplete() ? 1 : 0;
            case "WATCHLIST_READY" -> bounded(metrics.watchlistItems(), mission.target());
            case "FIRST_ORDER" -> metrics.firstOrderPlaced() ? 1 : 0;
            case "FIRST_EXECUTION" -> bounded(metrics.totalTrades(), mission.target());
            case "MULTI_MARKET_EXPLORER" -> metrics.tradedMarkets();
            case "DIVERSIFICATION_FOUNDATIONS" -> bounded(
                metrics.activeHoldings(),
                mission.target()
            );
            case "THREE_DAY_STREAK" -> bounded(
                metrics.effectiveStreak(),
                mission.target()
            );
            default -> 0;
        };
        return new MissionResponse(
            mission.code(),
            mission.title(),
            mission.description(),
            mission.xp(),
            progress,
            mission.target(),
            completion != null,
            completion == null ? null : completion.getCompletedAt()
        );
    }

    private List<AchievementResponse> achievementResponses(
        Map<String, AchievementUnlock> unlocked
    ) {
        return ACHIEVEMENTS.stream().map(definition -> {
            AchievementUnlock achievement = unlocked.get(definition.code());
            return new AchievementResponse(
                definition.code(),
                definition.title(),
                definition.description(),
                achievement != null,
                achievement == null ? null : achievement.getUnlockedAt()
            );
        }).toList();
    }

    private LearnerProgression lockedProgression(UUID userId) {
        Instant now = clock.instant();
        progressions.ensureExists(userId, now);
        return progressions.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new IllegalStateException("Progression could not be initialized"));
    }

    private AppUser requireUser(UUID userId) {
        return users.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    }

    private ZoneId zoneId(UUID userId) {
        String value = reportPreferences.findByUserId(userId)
            .map(WeeklyReportPreference::getZoneId)
            .orElse(WeeklyReportPreference.DEFAULT_ZONE_ID);
        try {
            return ZoneId.of(value);
        } catch (ZoneRulesException exception) {
            return ZoneId.of(WeeklyReportPreference.DEFAULT_ZONE_ID);
        }
    }

    private int effectiveStreak(LearnerProgression progression, LocalDate today) {
        LocalDate last = progression.getLastCheckInDate();
        if (last == null || last.isBefore(today.minusDays(1))) {
            return 0;
        }
        return progression.getCurrentStreak();
    }

    private LevelDefinition level(int totalXp) {
        LevelDefinition current = LEVELS.getFirst();
        for (LevelDefinition candidate : LEVELS) {
            if (candidate.floorXp() > totalXp) {
                break;
            }
            current = candidate;
        }
        return current;
    }

    private int bounded(long value, int target) {
        return (int) Math.min(Math.max(0L, value), target);
    }

    private record MissionDefinition(
        String code,
        String challengeCode,
        String title,
        String description,
        int xp,
        int target,
        Predicate<ProgressMetrics> complete
    ) {
    }

    private record ChallengeDefinition(
        String code,
        String title,
        String description
    ) {
    }

    private record LevelDefinition(int level, String name, int floorXp) {
    }

    private record AchievementDefinition(
        String code,
        String title,
        String description,
        Predicate<AchievementState> unlocked
    ) {
    }

    private record AchievementState(
        int totalXp,
        int completedMissions,
        java.util.Set<String> completedCodes
    ) {
    }

    private record ProgressMetrics(
        boolean onboardingComplete,
        boolean firstOrderPlaced,
        long watchlistItems,
        long indiaTrades,
        long unitedStatesTrades,
        long activeHoldings,
        int effectiveStreak
    ) {
        long totalTrades() {
            return indiaTrades + unitedStatesTrades;
        }

        int tradedMarkets() {
            return (indiaTrades > 0 ? 1 : 0) + (unitedStatesTrades > 0 ? 1 : 0);
        }
    }
}
