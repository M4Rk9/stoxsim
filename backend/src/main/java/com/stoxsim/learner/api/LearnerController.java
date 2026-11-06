package com.stoxsim.learner.api;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.stoxsim.learner.service.LearnerService;

@RestController
@RequestMapping("/api/v1/learner")
public class LearnerController {
    private final LearnerService learners;
    public LearnerController(LearnerService learners) { this.learners = learners; }
    @GetMapping("/profile") public LearnerProfile profile(@AuthenticationPrincipal Jwt jwt) { return learners.profile(user(jwt)); }
    @PutMapping("/profile") public LearnerProfile update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LearnerProfile.Update body) { return learners.update(user(jwt), body); }
    @GetMapping("/home") public LearnerHome home(@AuthenticationPrincipal Jwt jwt) { return learners.home(user(jwt)); }
    @PostMapping("/recommendations/{id}/used") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void used(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) { learners.used(user(jwt), id); }
    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
