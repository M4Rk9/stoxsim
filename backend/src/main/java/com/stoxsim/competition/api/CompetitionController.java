package com.stoxsim.competition.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.competition.service.CompetitionService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1")
public class CompetitionController {

    private final CompetitionService competitions;

    public CompetitionController(CompetitionService competitions) {
        this.competitions = competitions;
    }

    @GetMapping("/competitions/current")
    public CompetitionBoardResponse current(@AuthenticationPrincipal Jwt jwt) {
        return competitions.currentBoard(userId(jwt));
    }

    @PostMapping("/competitions/current/enroll")
    public CompetitionBoardResponse enroll(@AuthenticationPrincipal Jwt jwt) {
        return competitions.enroll(userId(jwt));
    }

    @GetMapping("/leagues")
    public List<LeagueSummaryResponse> leagues(@AuthenticationPrincipal Jwt jwt) {
        return competitions.leagues(userId(jwt));
    }

    @PostMapping("/leagues")
    @ResponseStatus(HttpStatus.CREATED)
    public LeagueCreatedResponse createLeague(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateLeagueRequest request
    ) {
        return competitions.createLeague(userId(jwt), request.name());
    }

    @PostMapping("/leagues/join")
    public LeagueDetailResponse joinLeague(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody JoinLeagueRequest request
    ) {
        return competitions.joinLeague(userId(jwt), request.inviteCode());
    }

    @GetMapping("/leagues/{leagueId}")
    public LeagueDetailResponse league(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID leagueId
    ) {
        return competitions.league(userId(jwt), leagueId);
    }

    @PostMapping("/leagues/{leagueId}/invite/rotate")
    public LeagueInviteResponse rotateInvite(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID leagueId
    ) {
        return competitions.rotateInvite(userId(jwt), leagueId);
    }

    @PostMapping("/leagues/{leagueId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveLeague(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID leagueId
    ) {
        competitions.leaveLeague(userId(jwt), leagueId);
    }

    @DeleteMapping("/leagues/{leagueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLeague(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID leagueId
    ) {
        competitions.deleteLeague(userId(jwt), leagueId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
