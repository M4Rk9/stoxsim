package com.stoxsim.progression.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.repository.HoldingRepository;
import com.stoxsim.progression.domain.AchievementUnlock;
import com.stoxsim.progression.domain.LearnerProgression;
import com.stoxsim.progression.domain.MissionCompletion;
import com.stoxsim.progression.repository.AchievementUnlockRepository;
import com.stoxsim.progression.repository.LearnerProgressionRepository;
import com.stoxsim.progression.repository.MissionCompletionRepository;
import com.stoxsim.report.repository.WeeklyReportPreferenceRepository;
import com.stoxsim.trade.repository.TradeRepository;
import com.stoxsim.watchlist.repository.WatchlistItemRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class ProgressionServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-31T03:15:00Z");

    @Mock private AppUserRepository users;
    @Mock private LearnerProgressionRepository progressions;
    @Mock private MissionCompletionRepository completions;
    @Mock private AchievementUnlockRepository achievements;
    @Mock private TradeRepository trades;
    @Mock private HoldingRepository holdings;
    @Mock private WatchlistItemRepository watchlistItems;
    @Mock private WeeklyReportPreferenceRepository reportPreferences;

    private Clock clock;
    private SimpleMeterRegistry meterRegistry;
    private AppUser user;
    private LearnerProgression progression;
    private List<MissionCompletion> savedCompletions;
    private List<AchievementUnlock> savedAchievements;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        meterRegistry = new SimpleMeterRegistry();
        user = new AppUser("learner@example.com", "hash", "Learner");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        progression = new LearnerProgression(USER_ID, NOW);
        savedCompletions = new ArrayList<>();
        savedAchievements = new ArrayList<>();

        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(progressions.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(progression));
        when(reportPreferences.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(completions.findAllByUserIdOrderByCompletedAt(USER_ID))
            .thenAnswer(invocation -> List.copyOf(savedCompletions));
        lenient().when(completions.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                MissionCompletion completion = invocation.getArgument(0);
                savedCompletions.add(completion);
                return completion;
            });
        when(achievements.findAllByUserIdOrderByUnlockedAt(USER_ID))
            .thenAnswer(invocation -> List.copyOf(savedAchievements));
        lenient().when(achievements.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                AchievementUnlock achievement = invocation.getArgument(0);
                savedAchievements.add(achievement);
                return achievement;
            });
    }

    @Test
    void reconcilesAuthoritativeMissionsWithoutDoubleAwardingXp() {
        user.completeOnboardingIntroduction();
        user.recordFirstOrderPlaced();
        progression.checkIn(LocalDate.of(2026, 8, 29), NOW);
        progression.checkIn(LocalDate.of(2026, 8, 30), NOW);
        progression.checkIn(LocalDate.of(2026, 8, 31), NOW);
        when(watchlistItems.countByUserId(USER_ID)).thenReturn(1L);
        when(trades.countByUserIdAndMarketRegion(USER_ID, MarketRegion.INDIA)).thenReturn(1L);
        when(trades.countByUserIdAndMarketRegion(USER_ID, MarketRegion.UNITED_STATES)).thenReturn(1L);
        when(holdings.countActiveByUserId(USER_ID)).thenReturn(3L);

        var first = service().state(USER_ID);
        var second = service().state(USER_ID);

        assertThat(first.version()).isEqualTo("learning-progression-v1");
        assertThat(first.totalXp()).isEqualTo(540);
        assertThat(first.level()).isEqualTo(4);
        assertThat(first.challenges()).extracting(challenge -> challenge.completedMissions())
            .containsExactly(3, 3, 1);
        assertThat(first.achievements()).allMatch(achievement -> achievement.unlocked());
        assertThat(second.totalXp()).isEqualTo(540);
        assertThat(savedCompletions).hasSize(7);
    }

    @Test
    void checkInIsIdempotentForTheUsersLocalDate() {
        when(watchlistItems.countByUserId(USER_ID)).thenReturn(0L);
        when(trades.countByUserIdAndMarketRegion(USER_ID, MarketRegion.INDIA)).thenReturn(0L);
        when(trades.countByUserIdAndMarketRegion(USER_ID, MarketRegion.UNITED_STATES)).thenReturn(0L);
        when(holdings.countActiveByUserId(USER_ID)).thenReturn(0L);

        var first = service().checkIn(USER_ID);
        var second = service().checkIn(USER_ID);

        assertThat(first.currentStreak()).isEqualTo(1);
        assertThat(second.currentStreak()).isEqualTo(1);
        assertThat(second.checkedInToday()).isTrue();
        assertThat(meterRegistry.counter(
            "stoxsim.progression.check_in",
            "result",
            "recorded"
        ).count()).isEqualTo(1);
        assertThat(meterRegistry.counter(
            "stoxsim.progression.check_in",
            "result",
            "duplicate"
        ).count()).isEqualTo(1);
    }

    private ProgressionService service() {
        return new ProgressionService(
            users,
            progressions,
            completions,
            achievements,
            trades,
            holdings,
            watchlistItems,
            reportPreferences,
            clock,
            meterRegistry
        );
    }
}
