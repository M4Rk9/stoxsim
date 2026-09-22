package com.stoxsim.campus.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;
import com.stoxsim.competition.api.StandingResponse;

public final class CampusCompetitionDtos {
    private CampusCompetitionDtos() { }
    public record Institution(UUID id, String name, String emailDomain, boolean suspended) { }
    public record Member(UUID userId, String displayName, String role, Instant joinedAt) { }
    public record JoinRequest(UUID id, UUID institutionId, String institutionName, UUID userId,
        String displayName, String email, String note, String status, String reviewNote, Instant submittedAt) { }
    public record Audit(String action, String actor, String target, String competition, Instant createdAt) { }
    public record Competition(UUID id, UUID institutionId, String title, Instant startsAt, Instant endsAt,
        int capacity, String status, int participants, boolean enrolled, boolean withdrawn, String cancellationNote) { }
    public record Workspace(Institution institution, String viewerRole, List<Competition> competitions) { }
    public record Management(List<Member> members, List<JoinRequest> requests, List<Audit> audit) { }
    public record Board(Competition competition, List<StandingResponse> standings,
        BigDecimal yourBaselineValue, BigDecimal yourLatestValue, boolean refreshUnavailable, String comparisonNote) { }
    public record Apply(@NotBlank @Size(max=300) String note) { }
    public record Review(@NotNull Boolean approve, @NotBlank @Size(max=500) String note) { }
    public record NewCompetition(@NotBlank @Size(min=3,max=100) String title,
        Instant startsAt, @NotNull Instant endsAt, @Min(2) @Max(200) int capacity) { }
    public record Reason(@NotBlank @Size(max=500) String note) { }
    public record MemberRole(@NotNull @Pattern(regexp="ORGANIZER|MEMBER") String role) { }
    public record Suspension(@NotNull Boolean suspended, @NotBlank @Size(max=500) String note) { }
    public record Organizer(@NotBlank @Email @Size(max=320) String email, @NotBlank @Size(max=500) String note) { }
}
