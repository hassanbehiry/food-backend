package com.food.foodapp.auth.security;

import com.food.foodapp.auth.jwt.JwtUtil;
import com.food.foodapp.common.exception.UnauthenticatedException;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtCookieUserContextTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private HttpServletRequest request;

    private JwtCookieUserContext userContext;

    @BeforeEach
    void setUp() {
        userContext = new JwtCookieUserContext(jwtUtil, request);
    }

    @Test
    void getCurrentUserId_returnsParsedId_whenAuthCookiePresent() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("auth_token", "valid.jwt.token")});
        when(jwtUtil.parseUserId("valid.jwt.token")).thenReturn(7L);

        assertThat(userContext.getCurrentUserId()).isEqualTo(7L);
    }

    @Test
    void getCurrentUserId_findsAuthCookie_amongOthers() {
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("other_cookie", "irrelevant"),
                new Cookie("auth_token", "valid.jwt.token")});
        when(jwtUtil.parseUserId("valid.jwt.token")).thenReturn(7L);

        assertThat(userContext.getCurrentUserId()).isEqualTo(7L);
    }

    @Test
    void getCurrentUserId_throwsUnauthenticated_whenNoCookies() {
        when(request.getCookies()).thenReturn(null);

        assertThatThrownBy(() -> userContext.getCurrentUserId()).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void getCurrentUserId_throwsUnauthenticated_whenAuthCookieMissing() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("other_cookie", "irrelevant")});

        assertThatThrownBy(() -> userContext.getCurrentUserId()).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void getCurrentUserId_throwsUnauthenticated_whenTokenInvalid() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("auth_token", "expired.jwt.token")});
        when(jwtUtil.parseUserId("expired.jwt.token")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        assertThatThrownBy(() -> userContext.getCurrentUserId()).isInstanceOf(UnauthenticatedException.class);
    }
}
