package com.food.foodapp.auth.mail;

/**
 * Delivers a password-reset link to a user. Exactly one implementation is active at runtime,
 * selected in {@link PasswordResetMailerConfig}:
 * <ul>
 *   <li>{@link JavaMailSenderPasswordResetMailer} when {@code spring.mail.host} is configured —
 *       sends a real plain-text email.</li>
 *   <li>{@link LoggingPasswordResetMailer} otherwise — a DEV-ONLY fallback that only logs the
 *       link (and WARNs that no mail transport is configured).</li>
 * </ul>
 * The {@code rawToken} passed here is the single secret that grants a reset; implementations must
 * never persist it and only the dev logging mailer may write it to a log.
 */
public interface PasswordResetMailer {

    /**
     * @param email    the recipient address (already normalized to the stored form)
     * @param rawToken the raw, un-hashed reset token to embed in the link
     */
    void sendResetLink(String email, String rawToken);
}
