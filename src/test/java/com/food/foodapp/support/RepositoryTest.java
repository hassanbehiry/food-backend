package com.food.foodapp.support;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @DataJpaTest} against the real (Docker Compose / CI) PostgreSQL database, with Flyway
 * migrations applied first.
 *
 * <p>The project's schema is owned by Flyway (a {@code V1} baseline plus versioned migrations) and
 * Hibernate runs in {@code validate} mode ({@code spring.jpa.hibernate.ddl-auto=validate}). The
 * standard {@code @DataJpaTest} slice does not import Flyway, so on a fresh database it would have
 * no schema to validate against. Pulling in {@link FlywayAutoConfiguration} makes the slice migrate
 * the database itself, exactly as the full application does on boot.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
public @interface RepositoryTest {
}
