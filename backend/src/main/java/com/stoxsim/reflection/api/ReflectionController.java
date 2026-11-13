package com.stoxsim.reflection.api;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.stoxsim.finwiz.api.FinwizRequest;
import com.stoxsim.finwiz.api.FinwizResponse;
import com.stoxsim.finwiz.service.FinwizService;
import com.stoxsim.onboarding.service.GuidedJourneyService;
import com.stoxsim.reflection.service.ReflectionService;

@RestController
@RequestMapping("/api/v1/reflections")
public class ReflectionController {
    private final ReflectionService reflections;
    private final FinwizService finwiz;
    private final GuidedJourneyService journey;
    public ReflectionController(ReflectionService reflections, FinwizService finwiz, GuidedJourneyService journey) {
        this.reflections = reflections; this.finwiz = finwiz; this.journey = journey;
    }
    @GetMapping public List<ReflectionResponse> list(@AuthenticationPrincipal Jwt jwt) { return reflections.list(user(jwt)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ReflectionResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReflectionRequest body) {
        return reflections.create(user(jwt), body);
    }
    @PutMapping("/{id}")
    public ReflectionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ReflectionRequest body) {
        return reflections.update(user(jwt), id, body);
    }
    public record LinkOrderRequest(@NotNull UUID orderId) { }
    @PostMapping("/{id}/order")
    public ReflectionResponse linkOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody LinkOrderRequest body) {
        return reflections.linkOrder(user(jwt), id, body.orderId());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { reflections.delete(user(jwt), id); }

    public record AssistanceRequest(
        @NotNull @AssertTrue(message = "Explicit consent is required to share this reflection with Finwiz") Boolean consent
    ) { }

    @PostMapping("/{id}/assistance")
    public FinwizResponse assist(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody AssistanceRequest body) {
        var reflection = reflections.get(user(jwt), id);
        // Only this explicit request sends stored private notes to AI. Reading/saving never does.
        String question = "Help me review the reasoning in this simulated decision. Treat my notes as unverified claims, not market facts. "
            + "Explain assumptions and questions to investigate, without real-money advice. Excerpts may be shortened.\n"
            + "Reason: " + excerpt(reflection.reason()) + "\nAssumptions: " + excerpt(reflection.assumptions())
            + "\nRisks: " + excerpt(reflection.risks()) + "\nExpected outcome: " + excerpt(reflection.expectedOutcome())
            + "\nSubsequent reflection: " + excerpt(reflection.reflection());
        var result = finwiz.ask(user(jwt), new FinwizRequest(question, FinwizRequest.Topic.PORTFOLIO_EDUCATION, null, null, null, null));
        journey.record(user(jwt), GuidedJourneyService.Event.FINWIZ);
        return result;
    }
    private static String excerpt(String text) {
        int end = Math.min(300, text.length());
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
