package com.food.foodapp.auth.service;

import com.food.foodapp.auth.dto.AdminUserListResponse;
import com.food.foodapp.auth.dto.AdminUserResponse;
import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.entity.UserStatus;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.common.exception.InvalidRequestParameterException;
import com.food.foodapp.common.exception.InvalidUserStatusTransitionException;
import com.food.foodapp.common.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository);
    }

    @Test
    void listUsers_listsEveryUser_whenNoFilterGiven() {
        when(userRepository.findAll((Specification<User>) isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user(1L, Role.CUSTOMER, UserStatus.ACTIVE)), Pageable.ofSize(20), 1));

        AdminUserListResponse response = adminUserService.listUsers(null, null, 0, 20);

        assertThat(response.getUsers()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listUsers_appliesRoleFilter() {
        ArgumentCaptor<Specification<User>> captor = ArgumentCaptor.forClass(Specification.class);
        when(userRepository.findAll(captor.capture(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user(1L, Role.OWNER, UserStatus.ACTIVE)), Pageable.ofSize(20), 1));

        AdminUserListResponse response = adminUserService.listUsers("owner", null, 0, 20);

        assertThat(response.getUsers()).hasSize(1);
        assertThat(response.getUsers().get(0).getRole()).isEqualTo(Role.OWNER);
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void listUsers_appliesStatusFilter() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user(1L, Role.CUSTOMER, UserStatus.SUSPENDED)), Pageable.ofSize(20), 1));

        AdminUserListResponse response = adminUserService.listUsers(null, "suspended", 0, 20);

        assertThat(response.getUsers().get(0).getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void listUsers_rejectsInvalidRoleValue() {
        assertThatThrownBy(() -> adminUserService.listUsers("banana", null, 0, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void listUsers_rejectsInvalidStatusValue() {
        assertThatThrownBy(() -> adminUserService.listUsers(null, "banana", 0, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void listUsers_rejectsInvalidPagination() {
        assertThatThrownBy(() -> adminUserService.listUsers(null, null, -1, 20))
                .isInstanceOf(InvalidRequestParameterException.class);
        assertThatThrownBy(() -> adminUserService.listUsers(null, null, 0, 0))
                .isInstanceOf(InvalidRequestParameterException.class);
        assertThatThrownBy(() -> adminUserService.listUsers(null, null, 0, 51))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void getUser_returnsUser_whenFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.CUSTOMER, UserStatus.ACTIVE)));

        AdminUserResponse response = adminUserService.getUser(1L);

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void getUser_throwsNotFound_whenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUser(99L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateStatus_suspendsAnActiveUser() {
        User user = user(1L, Role.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserResponse response = adminUserService.updateStatus(1L, "SUSPENDED");

        assertThat(response.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void updateStatus_reactivatesASuspendedUser() {
        User user = user(1L, Role.CUSTOMER, UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserResponse response = adminUserService.updateStatus(1L, "active");

        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void updateStatus_rejectsReSuspendingAnAlreadySuspendedUser() {
        User user = user(1L, Role.CUSTOMER, UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.updateStatus(1L, "SUSPENDED"))
                .isInstanceOf(InvalidUserStatusTransitionException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateStatus_rejectsUnknownStatusValue() {
        assertThatThrownBy(() -> adminUserService.updateStatus(1L, "banana"))
                .isInstanceOf(InvalidRequestParameterException.class);
    }

    @Test
    void updateStatus_throwsNotFound_whenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.updateStatus(99L, "SUSPENDED"))
                .isInstanceOf(UserNotFoundException.class);
    }

    private User user(Long id, Role role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setName("Ali");
        user.setEmail("ali@example.com");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
