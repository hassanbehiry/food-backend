package com.food.foodapp.auth.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * DEV-ONLY fallback {@link PasswordResetMailer}: it does not send anything, it logs the reset link
 * at {@code INFO} so a developer can complete the flow locally, and it {@code WARN}s on every call
 * that no real mail transport is configured.
 * <p>
 * It is wired only when no other {@link PasswordResetMailer} bean exists (see
 * {@link PasswordResetMailerConfig}). The loud WARN is deliberate: a production deployment that
 * forgot to set {@code spring.mail.*} would otherwise "succeed" silently while every reset link
 * sits unread in the application log. Production MUST configure {@code spring.mail.host} so
 * {@link JavaMailSenderPasswordResetMailer} takes over.
 */
@Slf4j
public class LoggingPasswordResetMailer implements PasswordResetMailer {

    private final String linkBase;

    public LoggingPasswordResetMailer(String linkBase) {
        this.linkBase = linkBase;
    }

    @Override
    public void sendResetLink(String email, String rawToken) {
        log.warn("No mail transport configured (spring.mail.host unset) — password reset link is only "
                + "being logged, not emailed. Configure spring.mail.* for production.");
        log.info("Password reset link for {}: {}{}", email, linkBase, rawToken);
    }
}
