package com.food.foodapp.auth.service;

import com.food.foodapp.auth.entity.PasswordResetToken;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.mail.PasswordResetMailer;
import com.food.foodapp.auth.repository.PasswordResetTokenRepository;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.common.exception.PasswordResetTokenInvalidException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The password-reset flow: request a reset link, then consume the emailed token to set a new
 * password.
 * <p>
 * Security properties:
 * <ul>
 *   <li><b>No account enumeration</b> — {@link #requestReset} does the same observable thing
 *       (nothing the caller can see) whether or not an account exists; the controller always
 *       returns the same generic {@code 200}.</li>
 *   <li><b>Tokens are opaque and hashed at rest</b> — a {@value #TOKEN_BYTES}-byte
 *       {@link SecureRandom} value, URL-safe base64 for the link; only its SHA-256 hex digest is
 *       stored, so a database read cannot be replayed.</li>
 *   <li><b>Single use, time-boxed</b> — a token is accepted once, only while unused and before
 *       {@code expires_at}; completing a reset also invalidates the user's other outstanding
 *       tokens.</li>
 *   <li>The raw token is never returned in an HTTP response and never logged (except by the
 *       explicit DEV-only {@link com.food.foodapp.auth.mail.LoggingPasswordResetMailer}).</li>
 * </ul>
 * Password hashing reuses the existing {@link PasswordEncoder} bean — this service never touches
 * {@link AuthService}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String GENERIC_INVALID_MESSAGE = "Invalid or expired reset token";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;

    /** How long an issued token stays valid. ISO-8601 duration, e.g. {@code PT30M}. */
    @Value("${app.password-reset.ttl:PT30M}")
    private Duration ttl;

    /**
     * Issues a reset token for {@code email} if an active account exists, stores only its hash, and
     * hands the raw token to the {@link PasswordResetMailer}. Always completes without signalling
     * whether the account existed.
     */
    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();

        Optional<User> maybeUser = userRepository.findByEmail(normalizedEmail);
        if (maybeUser.isEmpty()) {
            // No account enumeration: identical outcome to the hit path from the caller's view.
            log.info("Password reset requested for an email with no account - no token issued");
            return;
        }
        User user = maybeUser.get();

        String rawToken = generateRawToken();

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(sha256Hex(rawToken));
        token.setExpiresAt(LocalDateTime.now().plus(ttl == null ? DEFAULT_TTL : ttl));
        tokenRepository.save(token);

        mailer.sendResetLink(user.getEmail(), rawToken);
        log.info("Password reset token issued for user id {}", user.getId());
    }

    /**
     * Consumes {@code rawToken} and sets the user's password to {@code newPassword}. The token must
     * exist, be unused, and be unexpired — otherwise a generic {@link
     * PasswordResetTokenInvalidException} ({@code 400}) is thrown. On success the token and the
     * user's other outstanding tokens are all marked used, in one transaction.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String presentedHash = sha256Hex(rawToken == null ? "" : rawToken);

        PasswordResetToken token = tokenRepository.findByTokenHash(presentedHash)
                .orElseThrow(() -> new PasswordResetTokenInvalidException(GENERIC_INVALID_MESSAGE));

        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PasswordResetTokenInvalidException(GENERIC_INVALID_MESSAGE);
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));

        LocalDateTime now = LocalDateTime.now();
        token.setUsedAt(now);
        // Also burn any other still-valid links for this user (flushes the two dirty entities above
        // first, so the password change and this token's used_at are persisted as part of the set).
        tokenRepository.markOutstandingTokensUsed(user.getId(), now);

        log.info("Password reset completed for user id {}", user.getId());
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 of the UTF-8 bytes, lower-case hex — 64 chars, fits {@code token_hash varchar(64)}. */
    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
