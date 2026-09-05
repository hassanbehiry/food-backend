package com.food.foodapp.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String message;
    private UserResponse user;

    /**
     * The signed JWT, present only on a successful login. Lets a frontend that cannot rely on the
     * {@code auth_token} HttpOnly cookie (e.g. a cross-site SPA, a native/mobile client, or any
     * caller that strips cookies) instead send it back as {@code Authorization: Bearer <token>} —
     * see {@link com.food.foodapp.auth.security.JwtCookieAuthenticationFilter}, which accepts
     * either transport. Omitted (via {@code @JsonInclude(NON_NULL)}) from every other response,
     * including registration and logout.
     */
    private String token;
}
