package com.stoxsim.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.analytics.api.CohortOverview;
import com.stoxsim.analytics.repository.*;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;

@Service
public class CohortAnalyticsService {
    private final AppUserRepository users;
    private final ActivityRepository activity;
    private final CohortRepository cohorts;
    private final Clock clock;
    public CohortAnalyticsService(AppUserRepository users,ActivityRepository activity,CohortRepository cohorts,Clock clock) {
        this.users=users;this.activity=activity;this.cohorts=cohorts;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public CohortOverview overview(UUID requester,LocalDate from,LocalDate to,CohortRepository.Group group) {
        var user=users.findById(requester).orElseThrow(()->new UnauthorizedException("User no longer exists"));
        if(!user.isPlatformAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform administrator access is required");
        var now=clock.instant(); var range=AnalyticsPeriod.of(from,to,now);
        var today=now.atZone(ZoneOffset.UTC).toLocalDate();
        var retained=today.minusDays(ProductActivityService.RETENTION_DAYS-1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var available=max(activity.startedAt(),retained);var journey=max(available,cohorts.strategyStartedAt());
        return new CohortOverview("research-cohorts-v1",range.from(),range.to(),group.name(),now,available,journey,
            cohorts.dimensionsStartedAt(),cohorts.rows(group,range.start(),range.end(),available,journey,now,today));
    }
    private static Instant max(Instant a,Instant b){return a.isAfter(b)?a:b;}
}
