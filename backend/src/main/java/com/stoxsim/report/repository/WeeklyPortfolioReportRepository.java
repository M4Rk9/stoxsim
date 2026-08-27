package com.stoxsim.report.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.stoxsim.report.domain.WeeklyPortfolioReport;

public interface WeeklyPortfolioReportRepository
    extends JpaRepository<WeeklyPortfolioReport, UUID> {

    Optional<WeeklyPortfolioReport> findByUserIdAndPeriodEnd(UUID userId, LocalDate periodEnd);

    Optional<WeeklyPortfolioReport> findFirstByUserIdAndPeriodEndBeforeOrderByPeriodEndDesc(
        UUID userId,
        LocalDate periodEnd
    );

    Page<WeeklyPortfolioReport> findAllByUserIdOrderByPeriodEndDesc(
        UUID userId,
        Pageable pageable
    );
}
