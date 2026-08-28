package com.food.foodapp.auth.jwt;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 3600000L);
    }

    @Test
    void parseUserId_returnsSubject_forTokenItGenerated() {
        User user = new User();
        user.setId(42L);
        user.setEmail("test@example.com");
        user.setRole(Role.CUSTOMER);

        String token = jwtUtil.generateToken(user);

        assertThat(jwtUtil.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    void parseUserId_throws_forGarbageToken() {
        assertThatThrownBy(() -> jwtUtil.parseUserId("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void parseUserId_throws_forTokenSignedWithDifferentSecret() {
        JwtUtil otherJwtUtil = new JwtUtil("a-completely-different-secret-key-also-256-bits-long", 3600000L);
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setRole(Role.CUSTOMER);
        String token = otherJwtUtil.generateToken(user);

        assertThatThrownBy(() -> jwtUtil.parseUserId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseUserId_throws_forExpiredToken() throws InterruptedException {
        JwtUtil shortLivedJwtUtil = new JwtUtil(SECRET, 1L);
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setRole(Role.CUSTOMER);
        String token = shortLivedJwtUtil.generateToken(user);
        Thread.sleep(10);

        assertThatThrownBy(() -> shortLivedJwtUtil.parseUserId(token)).isInstanceOf(JwtException.class);
    }
}
