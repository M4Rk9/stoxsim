package com.stoxsim.analytics.api;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.analytics.repository.CohortRepository.Group;
import com.stoxsim.analytics.service.CohortAnalyticsService;

@RestController
@RequestMapping("/api/v1/admin/analytics/cohorts")
public class CohortAnalyticsController {
    private final CohortAnalyticsService service;
    public CohortAnalyticsController(CohortAnalyticsService service){this.service=service;}
    @GetMapping public ResponseEntity<CohortOverview> overview(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required=false) String from,@RequestParam(required=false) String to,
        @RequestParam(defaultValue="WEEK") Group groupBy) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(service.overview(UUID.fromString(jwt.getSubject()),date(from),date(to),groupBy));
    }
    private LocalDate date(String value) {
        try{return value==null?null:LocalDate.parse(value);}
        catch(java.time.format.DateTimeParseException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dates must use YYYY-MM-DD");}
    }
}
