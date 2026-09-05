package com.food.foodapp.auth.repository;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.food.foodapp.support.RepositoryTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link UserRepository#countByRoleAndCreatedAtLessThan}, the query behind the admin
 * analytics overview's "registered customers" KPI, against a real Postgres instance (started via
 * {@code compose.yaml}).
 */
@RepositoryTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void countByRoleAndCreatedAtLessThan_countsMatchingRole_registeredBeforeBoundary() {
        User customerBefore = user("customer-before-" + System.nanoTime() + "@example.com", Role.CUSTOMER);
        User customerAfter = user("customer-after-" + System.nanoTime() + "@example.com", Role.CUSTOMER);
        User ownerBefore = user("owner-before-" + System.nanoTime() + "@example.com", Role.OWNER);
        entityManager.flush();
        setCreatedAt(customerBefore, LocalDateTime.of(2026, 8, 1, 0, 0));
        setCreatedAt(customerAfter, LocalDateTime.of(2026, 8, 20, 0, 0));
        setCreatedAt(ownerBefore, LocalDateTime.of(2026, 8, 1, 0, 0));
        entityManager.clear();

        long count = userRepository.countByRoleAndCreatedAtLessThan(Role.CUSTOMER, LocalDateTime.of(2026, 8, 10, 0, 0));

        assertThat(count).isEqualTo(1);
    }

    /** {@code createdAt} is {@code @CreationTimestamp}-generated, so boundary tests must overwrite it directly. */
    private void setCreatedAt(User user, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE users SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", user.getId())
                .executeUpdate();
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setRole(role);
        entityManager.persist(user);
        return user;
    }
}
