package com.food.foodapp.auth.service;

import com.food.foodapp.auth.dto.AuthResponse;
import com.food.foodapp.auth.dto.RegisterRequest;
import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.jwt.JwtUtil;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.common.exception.DuplicateEmailException;
import com.food.foodapp.common.exception.RestaurantRegistrationClosedException;
import com.food.foodapp.settings.service.PlatformSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PlatformSettingsService platformSettingsService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil, platformSettingsService);
    }

    @Test
    void register_createsCustomer_withoutCheckingRestaurantRegistrationToggle() {
        when(userRepository.existsByEmail("ali@example.com")).thenReturn(false);

        AuthResponse response = authService.register(registerRequest(Role.CUSTOMER));

        assertThat(response.getMessage()).isEqualTo("Registration successful");
        verify(userRepository).save(any());
        verify(platformSettingsService, never()).isRestaurantRegistrationAllowed();
    }

    @Test
    void register_createsOwner_whenRegistrationAllowed() {
        when(platformSettingsService.isRestaurantRegistrationAllowed()).thenReturn(true);
        when(userRepository.existsByEmail("ali@example.com")).thenReturn(false);

        AuthResponse response = authService.register(registerRequest(Role.OWNER));

        assertThat(response.getMessage()).isEqualTo("Registration successful");
        verify(userRepository).save(any());
    }

    @Test
    void register_rejectsOwner_whenRestaurantRegistrationClosed() {
        when(platformSettingsService.isRestaurantRegistrationAllowed()).thenReturn(false);

        assertThatThrownBy(() -> authService.register(registerRequest(Role.OWNER)))
                .isInstanceOf(RestaurantRegistrationClosedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("ali@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest(Role.CUSTOMER)))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
    }

    private RegisterRequest registerRequest(Role role) {
        RegisterRequest request = new RegisterRequest();
        request.setName("Ali");
        request.setEmail("ali@example.com");
        request.setPassword("password123");
        request.setRole(role);
        return request;
    }
}
