package com.stoxsim.scenario;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ScenarioController {
    private final ScenarioService service;
    public ScenarioController(ScenarioService service) { this.service=service; }
    @GetMapping("/scenarios") public ResponseEntity<?> catalog() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ScenarioService.CATALOG);
    }
    @PostMapping("/accounts/{accountId}/scenarios") public ResponseEntity<?> run(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId, @RequestBody ScenarioService.Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.run(UUID.fromString(jwt.getSubject()), accountId, request));
    }
}
