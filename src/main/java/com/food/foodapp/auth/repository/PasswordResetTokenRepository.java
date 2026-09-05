package com.food.foodapp.auth.repository;

import com.food.foodapp.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Looks a token up by its SHA-256 hex digest — the only form of the token this application
     * ever holds. Backed by {@code idx_password_reset_tokens_hash}.
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Marks every still-outstanding (unused) token for a user as used, in one statement. Called
     * after a successful reset so a second emailed link — or one an attacker separately triggered —
     * cannot be used against the account. {@code flushAutomatically} pushes the just-mutated
     * consumed token and the user's new password hash to the database first; {@code
     * clearAutomatically} then evicts the now-stale entities from the persistence context.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.usedAt IS NULL")
    int markOutstandingTokensUsed(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
