package com.stoxsim.auth.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.auth.domain.AppUser;

public record UserResponse(
    UUID id,
    String email,
    String displayName,
    Instant createdAt,
    List<AccountResponse> accounts
) {
    public static UserResponse from(AppUser user, List<VirtualAccount> accounts) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getCreatedAt(),
            accounts.stream().map(AccountResponse::from).toList()
        );
    }
}
