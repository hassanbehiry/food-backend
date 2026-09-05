package com.food.foodapp.auth.security;

import com.food.foodapp.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Sends {@code 403 Forbidden} when an authenticated caller lacks the authority a route requires —
 * e.g. a {@code CUSTOMER} or {@code OWNER} calling {@code /api/v1/admin/**}. Pairs with
 * {@link RestAuthenticationEntryPoint} (401 for unauthenticated) so the two cases stay distinct,
 * and emits the same {@link ErrorResponse} body as {@code GlobalExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("You do not have permission to access this resource")
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
