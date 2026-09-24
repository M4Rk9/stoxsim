package com.stoxsim.portfolio.api;

import java.util.UUID;
import com.stoxsim.portfolio.service.PortfolioHistoryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/portfolio/history")
public class PortfolioHistoryController {
    private final PortfolioHistoryService service;
    public PortfolioHistoryController(PortfolioHistoryService service) { this.service=service; }
    @GetMapping public ResponseEntity<?> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID accountId,@RequestParam(defaultValue="30") int days) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(UUID.fromString(jwt.getSubject()),accountId,days));
    }
}
