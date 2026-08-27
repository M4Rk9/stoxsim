package com.stoxsim.competition.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.competition.domain.PrivateLeague;

import jakarta.persistence.LockModeType;

public interface PrivateLeagueRepository extends JpaRepository<PrivateLeague, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT league FROM PrivateLeague league WHERE league.inviteCodeHash = :hash")
    Optional<PrivateLeague> findByInviteCodeHashForUpdate(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT league FROM PrivateLeague league WHERE league.id = :id")
    Optional<PrivateLeague> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"season", "owner"})
    @Query("""
        SELECT member.league
        FROM LeagueMember member
        WHERE member.user.id = :userId
        ORDER BY member.joinedAt DESC
        """)
    List<PrivateLeague> findAllForMember(@Param("userId") UUID userId);

    long countByOwnerIdAndSeasonId(UUID ownerId, UUID seasonId);
}
