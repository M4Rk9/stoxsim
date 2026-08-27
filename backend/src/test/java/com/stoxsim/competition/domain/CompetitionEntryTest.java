package com.stoxsim.competition.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;

class CompetitionEntryTest {

    @Test
    void calculatesReturnFromTheLearnersEntryBaseline() {
        Instant joinedAt = Instant.parse("2026-08-01T00:00:00Z");
        var entry = new CompetitionEntry(
            null,
            new AppUser("learner@example.com", "hash", "Learner"),
            new BigDecimal("480000.00"),
            PricingStatus.CLOSED,
            joinedAt
        );

        entry.refresh(
            new BigDecimal("504000.00"),
            PricingStatus.LIVE,
            Instant.parse("2026-08-02T00:00:00Z")
        );

        assertThat(entry.getBaselineValue()).isEqualByComparingTo("480000.0000");
        assertThat(entry.getLatestValue()).isEqualByComparingTo("504000.0000");
        assertThat(entry.getReturnPercent()).isEqualByComparingTo("5.0000");
        assertThat(entry.getJoinedAt()).isEqualTo(joinedAt);
        assertThat(entry.getDataStatus()).isEqualTo(PricingStatus.LIVE);
    }
}
