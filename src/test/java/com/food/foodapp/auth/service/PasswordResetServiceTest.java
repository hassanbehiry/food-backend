package com.food.foodapp.auth.service;

import com.food.foodapp.auth.entity.PasswordResetToken;
import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.mail.PasswordResetMailer;
import com.food.foodapp.auth.repository.PasswordResetTokenRepository;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.common.exception.PasswordResetTokenInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure-Mockito coverage for {@link PasswordResetService} — no Spring context, no database. Verifies
 * the security-relevant behaviours: no account enumeration, token stored hashed (not raw), raw
 * token only handed to the mailer, single-use + expiry enforcement, and generic 400 for every
 * bad-token case.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetMailer mailer;

    @Captor
    private ArgumentCaptor<PasswordResetToken> tokenCaptor;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, passwordEncoder, mailer);
        ReflectionTestUtils.setField(service, "ttl", Duration.ofMinutes(30));
    }

    @Test
    void requestReset_knownEmail_storesHashedTokenAndEmailsRawLink() {
        User user = user(7L, "ali@example.com");
        when(userRepository.findByEmail("ali@example.com")).thenReturn(Optional.of(user));

        service.requestReset("  Ali@Example.com ");

        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();

        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendResetLink(eq("ali@example.com"), rawTokenCaptor.capture());
        String rawToken = rawTokenCaptor.getValue();

        // raw token is URL-safe base64 of >= 32 random bytes
        assertThat(rawToken).matches("^[A-Za-z0-9_-]+$");
        assertThat(Base64.getUrlDecoder().decode(rawToken)).hasSizeGreaterThanOrEqualTo(32);

        // only the hash is persisted — never the raw token
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(rawToken));
        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(rawToken);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(29));
    }

    @Test
    void requestReset_unknownEmail_issuesNothing_noEnumeration() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.requestReset("ghost@example.com");

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(mailer);
    }

    @Test
    void resetPassword_validToken_encodesNewPasswordAndBurnsTokens() {
        User user = user(7L, "ali@example.com");
        PasswordResetToken token = token(user, LocalDateTime.now().plusMinutes(10), null);
        // findByTokenHash is looked up by the SHA-256 of the presented raw token
        when(tokenRepository.findByTokenHash(sha256Hex("raw-token"))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newpassword1")).thenReturn("hashed-new");

        service.resetPassword("raw-token", "newpassword1");

        assertThat(user.getPassword()).isEqualTo("hashed-new");
        assertThat(token.getUsedAt()).isNotNull();
        verify(tokenRepository).markOutstandingTokensUsed(eq(7L), any(LocalDateTime.class));
    }

    @Test
    void resetPassword_unknownToken_throwsGeneric400() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("garbage", "newpassword1"))
                .isInstanceOf(PasswordResetTokenInvalidException.class)
                .hasMessage("Invalid or expired reset token");

        verify(passwordEncoder, never()).encode(any());
        verify(tokenRepository, never()).markOutstandingTokensUsed(anyLong(), any());
    }

    @Test
    void resetPassword_alreadyUsedToken_throwsGeneric400() {
        User user = user(7L, "ali@example.com");
        PasswordResetToken token = token(user, LocalDateTime.now().plusMinutes(10), LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("raw-token", "newpassword1"))
                .isInstanceOf(PasswordResetTokenInvalidException.class)
                .hasMessage("Invalid or expired reset token");

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void resetPassword_expiredToken_throwsGeneric400() {
        User user = user(7L, "ali@example.com");
        PasswordResetToken token = token(user, LocalDateTime.now().minusSeconds(1), null);
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("raw-token", "newpassword1"))
                .isInstanceOf(PasswordResetTokenInvalidException.class)
                .hasMessage("Invalid or expired reset token");

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void requestReset_issuesDistinctTokensAcrossCalls() {
        User user = user(7L, "ali@example.com");
        when(userRepository.findByEmail("ali@example.com")).thenReturn(Optional.of(user));

        service.requestReset("ali@example.com");
        service.requestReset("ali@example.com");

        verify(tokenRepository, org.mockito.Mockito.times(2)).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getAllValues().get(0).getTokenHash())
                .isNotEqualTo(tokenCaptor.getAllValues().get(1).getTokenHash());
    }

    @Test
    void resetPassword_nullToken_throwsGeneric400_withoutNpe() {
        when(tokenRepository.findByTokenHash(sha256Hex(""))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(null, "newpassword1"))
                .isInstanceOf(PasswordResetTokenInvalidException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static User user(long id, String email) {
        User user = new User();
        user.setId(id);
        user.setName("Ali");
        user.setEmail(email);
        user.setPassword("old-hash");
        user.setRole(Role.CUSTOMER);
        return user;
    }

    private static PasswordResetToken token(User user, LocalDateTime expiresAt, LocalDateTime usedAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash("stored-hash");
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        return token;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
