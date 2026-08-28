package com.food.foodapp.auth.security;

import com.food.foodapp.common.exception.UnauthenticatedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextHolderUserContextTest {

    private final SecurityContextHolderUserContext userContext = new SecurityContextHolderUserContext();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserId_returnsPrincipalId_whenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));

        assertThat(userContext.getCurrentUserId()).isEqualTo(7L);
    }

    @Test
    void getCurrentUserId_throwsUnauthenticated_whenContextIsEmpty() {
        assertThatThrownBy(userContext::getCurrentUserId).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void getCurrentUserId_throwsUnauthenticated_whenAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(userContext::getCurrentUserId).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void getCurrentUserId_throwsUnauthenticated_whenPrincipalIsNotAUserId() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("not-a-long", null, List.of()));

        assertThatThrownBy(userContext::getCurrentUserId).isInstanceOf(UnauthenticatedException.class);
    }
}
