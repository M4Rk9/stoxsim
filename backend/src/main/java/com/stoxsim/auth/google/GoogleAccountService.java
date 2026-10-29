package com.stoxsim.auth.google;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.stoxsim.account.service.AccountService;
import com.stoxsim.auth.api.dto.AuthResponse;
import com.stoxsim.auth.api.dto.UserResponse;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.AccountLifecycleService;
import com.stoxsim.auth.service.TokenService;
import com.stoxsim.common.error.ConflictException;
import com.stoxsim.common.error.UnauthorizedException;

@Service
@ConditionalOnProperty(name = "stoxsim.auth.google.enabled", havingValue = "true")
public class GoogleAccountService {
    private final JdbcTemplate db;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final AccountLifecycleService lifecycle;
    private final TokenService tokens;
    private final AccountService accounts;
    public GoogleAccountService(JdbcTemplate db, AppUserRepository users, PasswordEncoder passwords,
            AccountLifecycleService lifecycle, TokenService tokens, AccountService accounts) {
        this.db = db; this.users = users; this.passwords = passwords;
        this.lifecycle = lifecycle; this.tokens = tokens; this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public boolean linked(UUID userId) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM google_identity WHERE user_id = ?)", Boolean.class, userId));
    }

    @Transactional
    public void link(UUID userId, String password, String subject) {
        AppUser user = requirePassword(userId, password);
        int inserted = db.update("INSERT INTO google_identity(subject, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING", subject, userId);
        if (inserted != 1) throw new ConflictException("A Google account is already linked. Disconnect it before linking another account.");
        // Google email is intentionally not used to match, update or verify local email.
        lifecycle.audit(user.getId(), "GOOGLE_ACCOUNT_LINKED", null);
    }

    @Transactional
    public void unlink(UUID userId, String password) {
        requirePassword(userId, password);
        db.update("DELETE FROM google_identity WHERE user_id = ?", userId);
        lifecycle.revokeAllSessions(userId);
        lifecycle.audit(userId, "GOOGLE_ACCOUNT_UNLINKED", null);
    }

    @Transactional
    public AuthResponse login(String subject, String userAgent) {
        var ids = db.query("SELECT user_id FROM google_identity WHERE subject = ?", (rs, row) -> rs.getObject(1, UUID.class), subject);
        if (ids.isEmpty()) throw new UnauthorizedException("Link Google from your StoxSim settings before using Google sign-in.");
        AppUser user = users.findByIdForUpdate(ids.getFirst()).orElseThrow(() -> new UnauthorizedException("Account is unavailable"));
        // Re-check under the same user lock used by unlink, password reset and session creation.
        if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM google_identity WHERE subject = ? AND user_id = ?)", Boolean.class, subject, user.getId()))) {
            throw new UnauthorizedException("Google account is no longer linked");
        }
        var pair = tokens.issueTokenPair(user, userAgent);
        lifecycle.audit(user.getId(), "GOOGLE_SIGNED_IN", userAgent);
        return new AuthResponse(pair.accessToken(), pair.refreshToken(), "Bearer", pair.expiresInSeconds(),
            UserResponse.from(user, accounts.findByUserId(user.getId())));
    }

    private AppUser requirePassword(UUID userId, String password) {
        AppUser user = users.findByIdForUpdate(userId).orElseThrow(() -> new UnauthorizedException("Account is unavailable"));
        if (password == null || !passwords.matches(password, user.getPasswordHash())) throw new UnauthorizedException("Current password is incorrect");
        return user;
    }
}
