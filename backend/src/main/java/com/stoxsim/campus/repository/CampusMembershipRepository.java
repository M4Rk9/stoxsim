package com.stoxsim.campus.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.campus.domain.CampusMembership;

public interface CampusMembershipRepository
    extends JpaRepository<CampusMembership, UUID> {

    @Query("""
        SELECT membership
        FROM CampusMembership membership
        JOIN FETCH membership.institution
        WHERE membership.user.id = :userId
        """)
    Optional<CampusMembership> findByUserId(@Param("userId") UUID userId);

    boolean existsByUser_Id(UUID userId);
}
