package com.food.foodapp.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Selects the single active {@link PasswordResetMailer}.
 * <p>
 * When {@code spring.mail.host} is set, Spring Boot auto-configures a {@link JavaMailSender} and
 * {@link #javaMailSenderPasswordResetMailer} registers the real mailer. Otherwise that bean is
 * absent and {@link #loggingPasswordResetMailer} — declared second, guarded by
 * {@link ConditionalOnMissingBean} — provides the DEV-ONLY logging fallback. Declaration order in
 * this class is what makes the {@code @ConditionalOnMissingBean} deterministic.
 */
@Configuration
public class PasswordResetMailerConfig {

    @Bean
    @ConditionalOnProperty("spring.mail.host")
    PasswordResetMailer javaMailSenderPasswordResetMailer(
            JavaMailSender mailSender,
            @Value("${app.password-reset.link-base:http://localhost:5173/reset-password?token=}") String linkBase,
            @Value("${spring.mail.from:no-reply@foodhub.local}") String from) {
        return new JavaMailSenderPasswordResetMailer(mailSender, linkBase, from);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordResetMailer.class)
    PasswordResetMailer loggingPasswordResetMailer(
            @Value("${app.password-reset.link-base:http://localhost:5173/reset-password?token=}") String linkBase) {
        return new LoggingPasswordResetMailer(linkBase);
    }
}
