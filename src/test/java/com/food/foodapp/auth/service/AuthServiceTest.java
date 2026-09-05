package com.food.foodapp.auth.service;

import com.food.foodapp.auth.dto.AuthResponse;
import com.food.foodapp.auth.dto.LoginRequest;
import com.food.foodapp.auth.dto.RegisterRequest;
import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.entity.UserStatus;
import com.food.foodapp.auth.jwt.JwtUtil;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.common.exception.AccountSuspendedException;
import com.food.foodapp.common.exception.DuplicateEmailException;
import com.food.foodapp.common.exception.InvalidCredentialsException;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.RestaurantRegistrationClosedException;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.repository.RestaurantRepository;
import com.food.foodapp.settings.service.PlatformSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

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

    @Mock
    private RestaurantRepository restaurantRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, passwordEncoder, jwtUtil, platformSettingsService, restaurantRepository);
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
    void register_createsOwnerAndPendingRestaurant_whenRegistrationAllowed() {
        when(platformSettingsService.isRestaurantRegistrationAllowed()).thenReturn(true);
        when(userRepository.existsByEmail("ali@example.com")).thenReturn(false);

        RegisterRequest request = registerRequest(Role.OWNER);
        request.setRestaurantName("Ali's Kitchen");

        AuthResponse response = authService.register(request);

        assertThat(response.getMessage()).isEqualTo("Registration successful");
        verify(userRepository).save(any());

        ArgumentCaptor<Restaurant> restaurantCaptor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(restaurantCaptor.capture());
        Restaurant created = restaurantCaptor.getValue();
        assertThat(created.getName()).isEqualTo("Ali's Kitchen");
        assertThat(created.getApprovalStatus()).isEqualTo(RestaurantApprovalStatus.PENDING);
        assertThat(created.getOwner()).isNotNull();
    }

    @Test
    void register_rejectsOwner_whenRestaurantNameMissing() {
        when(platformSettingsService.isRestaurantRegistrationAllowed()).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest(Role.OWNER)))
                .isInstanceOf(InvalidRequestParameterException.class);
        verify(userRepository, never()).save(any());
        verify(restaurantRepository, never()).save(any());
    }

    @Test
    void register_doesNotCreateRestaurant_forCustomer() {
        when(userRepository.existsByEmail("ali@example.com")).thenReturn(false);

        authService.register(registerRequest(Role.CUSTOMER));

        verify(restaurantRepository, never()).save(any());
    }

    @Test
    void register_rejectsOwner_whenRestaurantRegistrationClosed() {
        when(platformSettingsService.isRestaurantRegistrationAllowed()).thenReturn(false);

        RegisterRequest request = registerRequest(Role.OWNER);
        request.setRestaurantName("Ali's Kitchen");

        assertThatThrownBy(() -> authService.register(request))
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

    @Test
    void login_returnsTokenAndUser_whenCredentialsValidAndAccountActive() {
        User user = existingUser(UserStatus.ACTIVE);
        when(userRepository.findByEmail("ali@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", user.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(user)).thenReturn("jwt-token");

        AuthService.LoginResult result = authService.login(loginRequest());

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.response().getMessage()).isEqualTo("Login successful");
        assertThat(result.response().getUser().getEmail()).isEqualTo("ali@example.com");
    }

    @Test
    void login_rejectsInvalidCredentials_whenPasswordWrong() {
        User user = existingUser(UserStatus.ACTIVE);
        when(userRepository.findByEmail("ali@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_rejectsInvalidCredentials_whenEmailNotFound() {
        when(userRepository.findByEmail("ali@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_rejectsSuspendedAccount_afterVerifyingPassword() {
        User user = existingUser(UserStatus.SUSPENDED);
        when(userRepository.findByEmail("ali@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(AccountSuspendedException.class);
        verify(jwtUtil, never()).generateToken(any());
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ali@example.com");
        request.setPassword("password123");
        return request;
    }

    private User existingUser(UserStatus status) {
        User user = new User();
        user.setId(1L);
        user.setName("Ali");
        user.setEmail("ali@example.com");
        user.setPassword("hashed-password");
        user.setRole(Role.CUSTOMER);
        user.setStatus(status);
        return user;
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
