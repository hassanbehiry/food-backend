package com.food.foodapp.auth.security;

import com.food.foodapp.auth.jwt.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtCookieAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    private JwtCookieAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        filter = new JwtCookieAuthenticationFilter(jwtUtil);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void populatesSecurityContext_whenAuthCookieHoldsAValidToken() throws Exception {
        request.setCookies(new Cookie("auth_token", "valid.jwt.token"));
        when(jwtUtil.parseUserId("valid.jwt.token")).thenReturn(7L);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void findsAuthCookie_amongOthers() throws Exception {
        request.setCookies(new Cookie("session", "x"), new Cookie("auth_token", "valid.jwt.token"));
        when(jwtUtil.parseUserId("valid.jwt.token")).thenReturn(7L);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
    }

    @Test
    void leavesContextUnauthenticated_whenNoCookiesPresent() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void leavesContextUnauthenticated_whenAuthCookieIsAbsent() throws Exception {
        request.setCookies(new Cookie("session", "x"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void leavesContextUnauthenticated_whenTokenIsInvalid() throws Exception {
        request.setCookies(new Cookie("auth_token", "expired.jwt.token"));
        when(jwtUtil.parseUserId("expired.jwt.token"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void doesNotOverwriteAnExistingAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(99L, null, java.util.List.of()));
        request.setCookies(new Cookie("auth_token", "valid.jwt.token"));
        lenient().when(jwtUtil.parseUserId("valid.jwt.token")).thenReturn(7L);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(99L);
    }
}
