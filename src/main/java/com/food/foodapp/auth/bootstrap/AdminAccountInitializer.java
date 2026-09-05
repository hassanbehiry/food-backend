package com.food.foodapp.auth.bootstrap;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.service.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a single {@code ROLE_ADMIN} account on startup, because there is deliberately no
 * self-service way to obtain one — {@code AuthService.register} rejects any role other than
 * {@code CUSTOMER}/{@code OWNER}.
 * <p>
 * Opt-in and idempotent: it does nothing unless both {@code app.admin.bootstrap.email} and
 * {@code app.admin.bootstrap.password} are set (as environment variables in real deployments — see
 * {@code application.properties}), and it never touches an account that already exists. Promoting or
 * demoting admins after this initial seed is an out-of-band DBA operation
 * ({@code UPDATE users SET role = 'ADMIN' WHERE email = ...}); this class only solves the
 * bootstrap problem of getting the very first admin in.
 * <p>
 * Field {@code @Value} injection with {@code @RequiredArgsConstructor} matches how {@code AuthController}
 * reads its cookie/JWT settings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap.email:}")
    private String bootstrapEmail;

    @Value("${app.admin.bootstrap.password:}")
    private String bootstrapPassword;

    @Value("${app.admin.bootstrap.name:Platform Admin}")
    private String bootstrapName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
            log.debug("Admin bootstrap skipped: app.admin.bootstrap.email/password not configured");
            return;
        }

        String email = bootstrapEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            log.info("Admin bootstrap skipped: a user with email '{}' already exists", email);
            return;
        }

        User admin = new User();
        admin.setName(bootstrapName.trim());
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(bootstrapPassword));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        log.info("Admin bootstrap: created ROLE_ADMIN account '{}'", email);
    }
}
