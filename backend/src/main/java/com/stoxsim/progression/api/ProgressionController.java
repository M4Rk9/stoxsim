package com.stoxsim.progression.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.progression.service.ProgressionService;

@RestController
@RequestMapping("/api/v1/progression")
public class ProgressionController {

    private final ProgressionService progression;

    public ProgressionController(ProgressionService progression) {
        this.progression = progression;
    }

    @GetMapping
    public ProgressionResponse state(@AuthenticationPrincipal Jwt jwt) {
        return progression.state(userId(jwt));
    }

    @PostMapping("/check-in")
    public ProgressionResponse checkIn(@AuthenticationPrincipal Jwt jwt) {
        return progression.checkIn(userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
