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
    private final com.stoxsim.subscription.service.SubscriptionService subscriptions;
    private final com.stoxsim.release.PublicReleaseGuard release;
    public GoogleAccountService(JdbcTemplate db, AppUserRepository users, PasswordEncoder passwords,
            AccountLifecycleService lifecycle, TokenService tokens, AccountService accounts,
            com.stoxsim.subscription.service.SubscriptionService subscriptions, com.stoxsim.release.PublicReleaseGuard release) {
        this.db = db; this.users = users; this.passwords = passwords;
        this.lifecycle = lifecycle; this.tokens = tokens; this.accounts = accounts; this.subscriptions = subscriptions; this.release = release;
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
        if (ids.isEmpty()) throw new UnauthorizedException("No account is linked to this Google account. Create an account with Google, or sign in with your password and link it in Settings.");
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

    @Transactional
    public AuthResponse register(GoogleIdentityVerifier.Identity identity, String displayName, boolean termsAccepted, String userAgent) {
        release.requirePublicRegistration();
        if (!termsAccepted) throw new IllegalArgumentException("You must accept the Terms and Privacy Notice");
        if (displayName == null || displayName.trim().length() < 2 || displayName.trim().length() > 100) {
            throw new IllegalArgumentException("Display name must contain 2–100 characters");
        }
        // Google does not establish current ownership of third-party email addresses.
        if (!identity.authoritativeEmail() || identity.email() == null || identity.email().length() > 320
                || !identity.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ConflictException("Use email registration for this Google account, then link Google in Settings.");
        }
        String email = identity.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email) || Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM google_identity WHERE subject = ?)", Boolean.class, identity.subject()))) {
            throw new ConflictException("An account already exists. Sign in, or use password recovery and link Google in Settings.");
        }
        AppUser user = new AppUser(email, null, displayName.trim());
        user.acceptLegalDocuments(com.stoxsim.auth.domain.LegalDocumentVersions.TERMS, com.stoxsim.auth.domain.LegalDocumentVersions.PRIVACY);
        user.markEmailVerified();
        try {
            user = users.saveAndFlush(user);
            accounts.createDefaultAccounts(user);
            subscriptions.initializeFree(user);
            db.update("INSERT INTO google_identity(subject, user_id) VALUES (?, ?)", identity.subject(), user.getId());
            lifecycle.audit(user.getId(), "ACCOUNT_REGISTERED", "google;terms=" + user.getTermsVersion() + ";privacy=" + user.getPrivacyVersion());
            return login(identity.subject(), userAgent);
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            throw new ConflictException("An account already exists. Sign in or recover your existing account.");
        }
    }

    @Transactional
    public void setInitialPassword(UUID userId, String subject, String newPassword) {
        AppUser user = requireIdentity(userId, subject);
        if (user.hasPassword()) throw new ConflictException("Use Change password or password recovery to replace an existing password.");
        user.changePassword(passwords.encode(newPassword));
        lifecycle.invalidateAccountLinks(userId);
        lifecycle.revokeAllSessions(userId);
        lifecycle.audit(userId, "PASSWORD_ADDED", null);
    }

    @Transactional
    public void deleteWithGoogle(UUID userId, String subject) {
        requireIdentity(userId, subject);
        lifecycle.deleteVerifiedAccount(userId);
    }

    private AppUser requireIdentity(UUID userId, String subject) {
        AppUser user = users.findByIdForUpdate(userId).orElseThrow(() -> new UnauthorizedException("Account is unavailable"));
        if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM google_identity WHERE user_id = ? AND subject = ?)",
                Boolean.class, userId, subject))) throw new UnauthorizedException("Choose the Google account linked to this StoxSim account.");
        return user;
    }

    private AppUser requirePassword(UUID userId, String password) {
        AppUser user = users.findByIdForUpdate(userId).orElseThrow(() -> new UnauthorizedException("Account is unavailable"));
        if (!user.hasPassword()) throw new ConflictException("Set a StoxSim password before disconnecting Google.");
        if (password == null || !passwords.matches(password, user.getPasswordHash())) throw new UnauthorizedException("Current password is incorrect");
        return user;
    }
}
