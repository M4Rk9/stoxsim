package com.stoxsim.campus.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.campus.domain.CampusRequestStatus;
import com.stoxsim.campus.domain.CampusVerificationRequest;

import jakarta.persistence.LockModeType;

public interface CampusVerificationRequestRepository
    extends JpaRepository<CampusVerificationRequest, UUID> {

    Optional<CampusVerificationRequest> findFirstByRequester_IdOrderBySubmittedAtDesc(
        UUID userId
    );

    @Query("""
        SELECT request
        FROM CampusVerificationRequest request
        JOIN FETCH request.requester
        WHERE request.status = :status
        ORDER BY request.submittedAt, request.id
        """)
    List<CampusVerificationRequest> findAllByStatus(
        @Param("status") CampusRequestStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT request
        FROM CampusVerificationRequest request
        JOIN FETCH request.requester
        WHERE request.id = :requestId
        """)
    Optional<CampusVerificationRequest> findByIdForUpdate(
        @Param("requestId") UUID requestId
    );
}
