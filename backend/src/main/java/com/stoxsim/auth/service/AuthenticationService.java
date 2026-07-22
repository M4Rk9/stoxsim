package com.stoxsim.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.service.AccountService;
import com.stoxsim.auth.api.dto.AccountResponse;
import com.stoxsim.auth.api.dto.AuthResponse;
import com.stoxsim.auth.api.dto.LoginRequest;
import com.stoxsim.auth.api.dto.RegisterRequest;
import com.stoxsim.auth.api.dto.UserResponse;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.repository.RefreshTokenRepository;
import com.stoxsim.common.error.ConflictException;
import com.stoxsim.common.error.UnauthorizedException;

@Service
public class AuthenticationService {

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;
    private final TokenService tokenService;

    public AuthenticationService(
        AppUserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        AccountService accountService,
        TokenService tokenService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountService = accountService;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account already exists for this email");
        }

        var user = userRepository.save(new AppUser(
            email,
            passwordEncoder.encode(request.password()),
            request.displayName().trim()
        ));
        List<VirtualAccount> accounts = accountService.createDefaultAccounts(user);
        return response(user, accounts, tokenService.issueTokenPair(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        var user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
            .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        return response(
            user,
            accountService.findByUserId(user.getId()),
            tokenService.issueTokenPair(user)
        );
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        var storedToken = refreshTokenRepository.findByTokenHash(tokenService.hash(rawRefreshToken))
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        Instant now = Instant.now();
        if (!storedToken.isActive(now)) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        storedToken.revoke(now);
        AppUser user = storedToken.getUser();
        return response(
            user,
            accountService.findByUserId(user.getId()),
            tokenService.issueTokenPair(user)
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(tokenService.hash(rawRefreshToken))
            .ifPresent(token -> token.revoke(Instant.now()));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        return UserResponse.from(user, accountService.findByUserId(userId));
    }

    private AuthResponse response(
        AppUser user,
        List<VirtualAccount> accounts,
        TokenService.TokenPair tokens
    ) {
        return new AuthResponse(
            tokens.accessToken(),
            tokens.refreshToken(),
            "Bearer",
            tokens.expiresInSeconds(),
            UserResponse.from(user, accounts)
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
