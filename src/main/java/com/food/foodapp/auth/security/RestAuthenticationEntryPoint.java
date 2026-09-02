package com.food.foodapp.auth.security;

import com.food.foodapp.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Sends {@code 401 Unauthorized} when an unauthenticated caller hits a route that requires
 * authentication — currently only {@code /api/v1/admin/**}, gated by {@link SecurityConfig}.
 * Spring Security's own default here is {@code Http403ForbiddenEntryPoint} (a 403), which would
 * make "not logged in" and "logged in but not an admin" indistinguishable; this restores the
 * conventional 401-vs-403 split.
 * <p>
 * The body is the same {@link ErrorResponse} shape {@code GlobalExceptionHandler} produces, so a
 * client sees one consistent error envelope whether the rejection happens in the security filter
 * chain or deeper in a controller.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message("Authentication required")
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
