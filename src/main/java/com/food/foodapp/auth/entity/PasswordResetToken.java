package com.food.foodapp.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A single-use password-reset token issued by {@code POST /api/v1/auth/forgot-password} and
 * consumed by {@code POST /api/v1/auth/reset-password}.
 * <p>
 * Only the SHA-256 hex digest of the token is persisted ({@link #tokenHash}, 64 chars) — the raw
 * token is emailed to the user and never stored, so a leak of this table cannot be replayed. A
 * token is valid only while {@link #usedAt} is {@code null} and {@link #expiresAt} is in the
 * future; {@code PasswordResetService} sets {@code usedAt} the moment a token completes a reset
 * and also invalidates the user's other outstanding tokens.
 * <p>
 * Column names / types mirror {@code V7__create_password_reset_tokens.sql} exactly so Hibernate
 * {@code validate} passes.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
