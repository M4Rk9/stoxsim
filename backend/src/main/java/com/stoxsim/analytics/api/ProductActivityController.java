package com.stoxsim.analytics.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.analytics.service.ProductActivityService;
import com.stoxsim.analytics.service.ProductActivityEvent.Kind;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;

@RestController
@RequestMapping("/api/v1/analytics/events")
public class ProductActivityController {
    private final ProductActivityService activity;
    private final AppUserRepository users;

    public ProductActivityController(ProductActivityService activity, AppUserRepository users) {
        this.activity = activity;
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<Void> capture(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> body) {
        if (!body.keySet().equals(Set.of("version", "event")) || !Integer.valueOf(1).equals(body.get("version"))
            || !("ACTIVE".equals(body.get("event")) || "STOCK_OPENED".equals(body.get("event")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected version 1 and ACTIVE or STOCK_OPENED only");
        }
        var userId = UUID.fromString(jwt.getSubject());
        if (!users.existsById(userId)) throw new UnauthorizedException("User no longer exists");
        activity.record(userId, Kind.valueOf((String) body.get("event")));
        return ResponseEntity.noContent().build();
    }
}
