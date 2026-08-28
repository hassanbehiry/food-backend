package com.food.foodapp.auth.security;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.jwt.JwtUtil;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.service.PasswordEncoder;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check that the Spring Security filter chain is actually wired: a request carrying a
 * valid {@code auth_token} cookie reaches a {@code UserContext}-dependent endpoint as an
 * authenticated caller, and one without a usable cookie is rejected. Exercises the real
 * {@link JwtCookieAuthenticationFilter} → {@link org.springframework.security.core.context.SecurityContextHolder}
 * → {@link SecurityContextHolderUserContext} path, with no mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityFilterChainIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String validToken;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setName("Security IT User");
        user.setEmail("security-it-" + System.nanoTime() + "@example.com");
        user.setPassword(passwordEncoder.encode("Passw0rd123"));
        user.setRole(Role.CUSTOMER);
        userRepository.save(user);
        validToken = jwtUtil.generateToken(user);
    }

    @Test
    void authenticatedEndpoint_isReachable_withAValidAuthTokenCookie() throws Exception {
        mockMvc.perform(get("/api/v1/cart").cookie(new Cookie("auth_token", validToken)))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedEndpoint_returns401_withoutAnyCookie() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedEndpoint_returns401_whenTheAuthTokenCookieIsGarbage() throws Exception {
        mockMvc.perform(get("/api/v1/cart").cookie(new Cookie("auth_token", "not-a-real-token")))
                .andExpect(status().isUnauthorized());
    }
}
