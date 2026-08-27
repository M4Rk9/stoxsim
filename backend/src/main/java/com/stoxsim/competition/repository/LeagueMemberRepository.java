package com.stoxsim.competition.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stoxsim.competition.domain.LeagueMember;

public interface LeagueMemberRepository extends JpaRepository<LeagueMember, UUID> {

    boolean existsByLeagueIdAndUserId(UUID leagueId, UUID userId);

    Optional<LeagueMember> findByLeagueIdAndUserId(UUID leagueId, UUID userId);

    long countByLeagueId(UUID leagueId);

    void deleteByLeagueIdAndUserId(UUID leagueId, UUID userId);
}
