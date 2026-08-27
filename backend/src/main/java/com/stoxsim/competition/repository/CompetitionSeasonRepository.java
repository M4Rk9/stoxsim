package com.stoxsim.competition.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.competition.domain.CompetitionSeason;

public interface CompetitionSeasonRepository
    extends JpaRepository<CompetitionSeason, UUID> {

    Optional<CompetitionSeason> findByCode(String code);

    @Modifying
    @Query(value = """
        INSERT INTO competition_season (
            code, title, starts_at, ends_at, created_at
        ) VALUES (:code, :title, :startsAt, :endsAt, :now)
        ON CONFLICT (code) DO NOTHING
        """, nativeQuery = true)
    void ensureSeason(
        @Param("code") String code,
        @Param("title") String title,
        @Param("startsAt") Instant startsAt,
        @Param("endsAt") Instant endsAt,
        @Param("now") Instant now
    );
}
