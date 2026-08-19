package com.stoxsim.auth.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.stoxsim.auth.api.dto.AccountEventResponse;
import com.stoxsim.auth.api.dto.SessionResponse;
import com.stoxsim.auth.config.AuthProperties;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.repository.RefreshTokenRepository;
import com.stoxsim.common.error.UnauthorizedException;

@Service
public class AccountLifecycleService {

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AccountTokenService accountTokenService;
    private final AccountMailService mailService;
    private final AuthProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public AccountLifecycleService(
        AppUserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        TokenService tokenService,
        AccountTokenService accountTokenService,
        AccountMailService mailService,
        AuthProperties properties,
        JdbcTemplate jdbcTemplate
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.accountTokenService = accountTokenService;
        this.mailService = mailService;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void sendVerification(UUID userId) {
        AppUser user = requireUser(userId);
        if (user.isEmailVerified()) {
            return;
        }
        String token = accountTokenService.issue(
            userId,
            AccountTokenService.EMAIL_VERIFICATION,
            Duration.ofMinutes(properties.getEmailVerificationMinutes())
        );
        mailService.sendVerification(user, token);
        audit(userId, "EMAIL_VERIFICATION_SENT", null);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        UUID userId = accountTokenService.consume(
            rawToken,
            AccountTokenService.EMAIL_VERIFICATION
        );
        AppUser user = requireUser(userId);
        user.markEmailVerified();
        audit(userId, "EMAIL_VERIFIED", null);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(email)).ifPresent(user -> {
            String token = accountTokenService.issue(
                user.getId(),
                AccountTokenService.PASSWORD_RESET,
                Duration.ofMinutes(properties.getPasswordResetMinutes())
            );
            mailService.sendPasswordReset(user, token);
            audit(user.getId(), "PASSWORD_RESET_REQUESTED", null);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UUID userId = accountTokenService.consume(
            rawToken,
            AccountTokenService.PASSWORD_RESET
        );
        AppUser user = requireUser(userId);
        user.changePassword(passwordEncoder.encode(newPassword));
        revokeAllSessions(userId);
        audit(userId, "PASSWORD_RESET", null);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions(UUID userId, String currentRawToken) {
        String currentHash = StringUtils.hasText(currentRawToken)
            ? tokenService.hash(currentRawToken)
            : null;
        return refreshTokenRepository.findActiveByUserId(userId, Instant.now())
            .stream()
            .map(token -> SessionResponse.from(
                token,
                currentHash != null && currentHash.equals(token.getTokenHash())
            ))
            .toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        var token = refreshTokenRepository.findActiveSession(userId, sessionId, Instant.now())
            .orElseThrow(() -> new UnauthorizedException("Session is no longer active"));
        token.revoke(Instant.now());
        audit(userId, "SESSION_REVOKED", token.getUserAgent());
    }

    @Transactional
    public void logoutAll(UUID userId) {
        revokeAllSessions(userId);
        audit(userId, "ALL_SESSIONS_REVOKED", null);
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        refreshTokenRepository.revokeAllActive(userId, Instant.now());
    }

    @Transactional
    public Map<String, Object> exportAccount(UUID userId) {
        requireUser(userId);
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now());
        export.put("profile", jdbcTemplate.queryForMap(
            """
            SELECT id, email, display_name, email_verified_at,
                   terms_accepted_at, terms_version, privacy_version,
                   created_at, updated_at
            FROM app_user
            WHERE id = ?
            """,
            userId
        ));
        export.put("accounts", jdbcTemplate.queryForList(
            "SELECT * FROM virtual_account WHERE user_id = ? ORDER BY market_region",
            userId
        ));
        export.put("holdings", jdbcTemplate.queryForList(
            """
            SELECT holding.*
            FROM holding
            JOIN virtual_account account ON account.id = holding.account_id
            WHERE account.user_id = ?
            ORDER BY holding.created_at
            """,
            userId
        ));
        export.put("orders", jdbcTemplate.queryForList(
            """
            SELECT paper_order.*
            FROM paper_order
            JOIN virtual_account account ON account.id = paper_order.account_id
            WHERE account.user_id = ?
            ORDER BY paper_order.created_at
            """,
            userId
        ));
        export.put("trades", jdbcTemplate.queryForList(
            """
            SELECT trade.*
            FROM trade
            JOIN virtual_account account ON account.id = trade.account_id
            WHERE account.user_id = ?
            ORDER BY trade.executed_at
            """,
            userId
        ));
        export.put("ledger", jdbcTemplate.queryForList(
            """
            SELECT account_ledger.*
            FROM account_ledger
            JOIN virtual_account account ON account.id = account_ledger.account_id
            WHERE account.user_id = ?
            ORDER BY account_ledger.created_at
            """,
            userId
        ));
        export.put("watchlists", jdbcTemplate.queryForList(
            "SELECT * FROM watchlist WHERE user_id = ? ORDER BY created_at",
            userId
        ));
        export.put("watchlistItems", jdbcTemplate.queryForList(
            """
            SELECT item.*
            FROM watchlist_item item
            JOIN watchlist list ON list.id = item.watchlist_id
            WHERE list.user_id = ?
            ORDER BY item.created_at
            """,
            userId
        ));
        export.put("securityEvents", events(userId));
        audit(userId, "ACCOUNT_DATA_EXPORTED", null);
        return export;
    }

    @Transactional
    public void deleteAccount(UUID userId, String password) {
        AppUser user = requireUser(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Password is incorrect");
        }
        audit(userId, "ACCOUNT_DELETED", "User requested permanent deletion");
        jdbcTemplate.update(
            "UPDATE account_event SET detail = NULL WHERE user_id = ?",
            userId
        );
        userRepository.delete(user);
        userRepository.flush();
    }

    @Transactional(readOnly = true)
    public List<AccountEventResponse> events(UUID userId) {
        return jdbcTemplate.query(
            """
            SELECT id, event_type, detail, created_at
            FROM account_event
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT 50
            """,
            (resultSet, rowNumber) -> new AccountEventResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getString("detail"),
                resultSet.getTimestamp("created_at").toInstant()
            ),
            userId
        );
    }

    public void audit(UUID userId, String type, String detail) {
        String safeDetail = StringUtils.hasText(detail)
            ? detail.substring(0, Math.min(detail.length(), 200))
            : null;
        jdbcTemplate.update(
            """
            INSERT INTO account_event (id, user_id, event_type, detail, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            userId,
            type,
            safeDetail,
            Timestamp.from(Instant.now())
        );
    }

    private AppUser requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
