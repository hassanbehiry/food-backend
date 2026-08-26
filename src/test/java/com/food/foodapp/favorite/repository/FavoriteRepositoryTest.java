package com.food.foodapp.favorite.repository;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.favorite.entity.Favorite;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FavoriteRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Test
    void findByCustomerIdWithRestaurant_returnsNewestFirst_forThatCustomerOnly() {
        User customer = persistUser("customer-" + System.nanoTime() + "@example.com");
        User other = persistUser("other-" + System.nanoTime() + "@example.com");
        Restaurant restaurantA = persistRestaurant("A-" + System.nanoTime());
        Restaurant restaurantB = persistRestaurant("B-" + System.nanoTime());

        Favorite older = persistFavorite(customer, restaurantA);
        Favorite newer = persistFavorite(customer, restaurantB);
        persistFavorite(other, restaurantA);
        entityManager.flush();
        entityManager.clear();

        List<Favorite> found = favoriteRepository.findByCustomerIdWithRestaurant(customer.getId());

        assertThat(found).extracting(Favorite::getId).containsExactly(newer.getId(), older.getId());
        assertThat(found.get(0).getRestaurant().getName()).isEqualTo(restaurantB.getName());
    }

    @Test
    void findByCustomerIdAndRestaurantId_isEmpty_forAnotherCustomersFavorite() {
        User owner = persistUser("owner-" + System.nanoTime() + "@example.com");
        User stranger = persistUser("stranger-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("R-" + System.nanoTime());
        persistFavorite(owner, restaurant);
        entityManager.flush();

        Optional<Favorite> found = favoriteRepository.findByCustomerIdAndRestaurantId(stranger.getId(), restaurant.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void findByCustomerIdAndRestaurantId_returnsFavorite_forItsOwner() {
        User owner = persistUser("owner-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("R-" + System.nanoTime());
        Favorite favorite = persistFavorite(owner, restaurant);
        entityManager.flush();

        Optional<Favorite> found = favoriteRepository.findByCustomerIdAndRestaurantId(owner.getId(), restaurant.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(favorite.getId());
    }

    @Test
    void duplicateFavorite_violatesUniqueConstraint_forSameCustomerAndRestaurant() {
        User customer = persistUser("customer-" + System.nanoTime() + "@example.com");
        Restaurant restaurant = persistRestaurant("R-" + System.nanoTime());
        persistFavorite(customer, restaurant);
        entityManager.flush();

        Favorite duplicate = new Favorite();
        duplicate.setCustomer(customer);
        duplicate.setRestaurant(restaurant);

        // Favorite's id is IDENTITY-generated, so Hibernate issues the insert immediately on
        // persist() rather than deferring it to flush() — the constraint violation surfaces here.
        assertThatThrownBy(() -> entityManager.persist(duplicate)).isInstanceOf(PersistenceException.class);
    }

    private User persistUser(String email) {
        User user = new User();
        user.setName("Favorite Owner");
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setRole(Role.CUSTOMER);
        entityManager.persist(user);
        return user;
    }

    private Restaurant persistRestaurant(String name) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setCuisine(name);
        restaurant.setDeliveryFee(BigDecimal.valueOf(10));
        restaurant.setMinimumOrder(BigDecimal.valueOf(30));
        restaurant.setEstimatedDeliveryMinMinutes(20);
        restaurant.setEstimatedDeliveryMaxMinutes(30);
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        restaurant.setOpenForOrders(true);
        entityManager.persist(restaurant);
        return restaurant;
    }

    private Favorite persistFavorite(User customer, Restaurant restaurant) {
        Favorite favorite = new Favorite();
        favorite.setCustomer(customer);
        favorite.setRestaurant(restaurant);
        entityManager.persistAndFlush(favorite);
        return favorite;
    }
}
