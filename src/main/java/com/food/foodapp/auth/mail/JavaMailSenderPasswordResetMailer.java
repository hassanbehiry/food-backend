package com.food.foodapp.auth.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Production {@link PasswordResetMailer}: sends a plain-text email containing the reset link via
 * the auto-configured {@link JavaMailSender}. Wired only when {@code spring.mail.host} is set (see
 * {@link PasswordResetMailerConfig}).
 * <p>
 * The raw token is written only into the outgoing message body — never logged. A send failure is
 * logged and rethrown so the caller's transaction rolls back the issued token rather than leaving
 * a token the user can never receive.
 */
@Slf4j
public class JavaMailSenderPasswordResetMailer implements PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final String linkBase;
    private final String from;

    public JavaMailSenderPasswordResetMailer(JavaMailSender mailSender, String linkBase, String from) {
        this.mailSender = mailSender;
        this.linkBase = linkBase;
        this.from = from;
    }

    @Override
    public void sendResetLink(String email, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(email);
        message.setSubject("Reset your FoodHub password");
        message.setText("""
                We received a request to reset your FoodHub password.

                Open the link below to choose a new password. It is valid for 30 minutes and can be used once:

                %s%s

                If you did not request this, you can safely ignore this email — your password will not change.
                """.formatted(linkBase, rawToken));

        try {
            mailSender.send(message);
        } catch (RuntimeException ex) {
            log.error("Failed to send password reset email to {}", email, ex);
            throw ex;
        }
    }
}
