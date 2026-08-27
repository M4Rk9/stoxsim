package com.stoxsim.report.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.stoxsim.report.domain.WeeklyReportPreference;

public interface WeeklyReportPreferenceRepository
    extends JpaRepository<WeeklyReportPreference, UUID> {

    Optional<WeeklyReportPreference> findByUserId(UUID userId);

    Page<WeeklyReportPreference> findAllByEnabledTrue(Pageable pageable);
}
