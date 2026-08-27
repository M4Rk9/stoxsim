package com.stoxsim.competition.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLeagueRequest(
    @NotBlank @Size(min = 3, max = 80) String name
) {
}
