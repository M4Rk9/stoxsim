package com.stoxsim.competition.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinLeagueRequest(
    @NotBlank @Size(min = 16, max = 80) String inviteCode
) {
}
