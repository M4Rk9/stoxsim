package com.stoxsim.competition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.TokenService;
import com.stoxsim.competition.domain.CompetitionEntry;
import com.stoxsim.competition.domain.CompetitionSeason;
import com.stoxsim.competition.repository.CompetitionEntryRepository;
import com.stoxsim.competition.repository.CompetitionSeasonRepository;
import com.stoxsim.competition.repository.LeagueMemberRepository;
import com.stoxsim.competition.repository.PrivateLeagueRepository;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.api.PortfolioResponse;
import com.stoxsim.portfolio.service.PortfolioValuationService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SEASON_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");

    @Mock private CompetitionSeasonRepository seasons;
    @Mock private CompetitionEntryRepository entries;
    @Mock private PrivateLeagueRepository leagues;
    @Mock private LeagueMemberRepository members;
    @Mock private AppUserRepository users;
    @Mock private PortfolioValuationService valuations;
    @Mock private TokenService tokens;

    private AppUser user;
    private CompetitionSeason season;

    @BeforeEach
    void setUp() {
        user = new AppUser("learner@example.com", "hash", "Learner");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        season = mock(CompetitionSeason.class);
        lenient().when(season.getId()).thenReturn(SEASON_ID);
        lenient().when(season.getCode()).thenReturn("2026-Q3");
        lenient().when(season.getTitle()).thenReturn("2026 Q3 Learning Season");
        lenient().when(season.getStartsAt()).thenReturn(Instant.parse("2026-07-01T00:00:00Z"));
        lenient().when(season.getEndsAt()).thenReturn(Instant.parse("2026-10-01T00:00:00Z"));
        lenient().when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(seasons.findByCode("2026-Q3")).thenReturn(Optional.of(season));
    }

    @Test
    void enrollmentUsesTheStandardPortfolioValueAsAnEntryBaseline() {
        var entry = new CompetitionEntry(
            season,
            user,
            new BigDecimal("485000.00"),
            PricingStatus.CLOSED,
            NOW
        );
        when(entries.findForUpdate(SEASON_ID, USER_ID))
            .thenReturn(Optional.empty(), Optional.of(entry));
        when(valuations.value(USER_ID, MarketRegion.INDIA))
            .thenReturn(portfolio("500000.00", "485000.00"));
        when(entries.findAllBySeasonIdOrderByReturnPercentDescJoinedAtAsc(
            org.mockito.ArgumentMatchers.eq(SEASON_ID),
            any(Pageable.class)
        )).thenReturn(List.of(entry));
        when(entries.countBySeasonId(SEASON_ID)).thenReturn(1L);
        when(entries.countBySeasonIdAndReturnPercentGreaterThan(SEASON_ID, BigDecimal.ZERO))
            .thenReturn(0L);

        var result = service().enroll(USER_ID);

        assertThat(result.enrolled()).isTrue();
        assertThat(result.yourBaselineValue()).isEqualByComparingTo("485000.0000");
        assertThat(result.yourRank()).isEqualTo(1);
        assertThat(result.season().scoringVersion())
            .isEqualTo(CompetitionService.SCORING_VERSION);
        verify(entries).ensureEntry(
            SEASON_ID,
            USER_ID,
            new BigDecimal("485000.00"),
            "CLOSED",
            NOW
        );
    }

    @Test
    void rejectsNonStandardSandboxCapitalFromCompetitionEnrollment() {
        when(entries.findForUpdate(SEASON_ID, USER_ID)).thenReturn(Optional.empty());
        when(valuations.value(USER_ID, MarketRegion.INDIA))
            .thenReturn(portfolio("2500000.00", "2500000.00"));

        assertThatThrownBy(() -> service().enroll(USER_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("standard ₹5 lakh India portfolio");
    }

    @Test
    void hidesPrivateLeagueExistenceFromNonMembers() {
        UUID foreignLeagueId = UUID.randomUUID();

        assertThatThrownBy(() -> service().league(USER_ID, foreignLeagueId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(cause -> assertThat(
                ((ResponseStatusException) cause).getStatusCode().value()
            ).isEqualTo(404));
    }

    private CompetitionService service() {
        return new CompetitionService(
            seasons,
            entries,
            leagues,
            members,
            users,
            valuations,
            tokens,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new SimpleMeterRegistry()
        );
    }

    private PortfolioResponse portfolio(String startingCapital, String accountValue) {
        return new PortfolioResponse(
            MarketRegion.INDIA,
            "INR",
            new BigDecimal(startingCapital),
            new BigDecimal(accountValue),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal(accountValue).subtract(new BigDecimal(startingCapital)),
            new BigDecimal(accountValue),
            BigDecimal.ZERO,
            PricingStatus.CLOSED,
            NOW,
            List.of()
        );
    }
}
