package com.food.foodapp.restaurant.repository;

import com.food.foodapp.category.entity.Category;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.food.foodapp.support.RepositoryTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link RestaurantSpecifications} and the category fetch-join query
 * against a real Postgres instance (started via {@code compose.yaml}), since this
 * is hand-written Criteria API code that a mocked unit test cannot validate.
 */
@RepositoryTest
class RestaurantRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Test
    void findAll_withSpecification_appliesApprovalAndCategorySlugFilter_keepingClosedButApproved() {
        String pizzaSlug = "pizza-" + System.nanoTime();
        String sushiSlug = "sushi-" + System.nanoTime();
        Category pizza = category("بيتزا-" + System.nanoTime(), pizzaSlug);
        Category sushi = category("سوشي-" + System.nanoTime(), sushiSlug);
        entityManager.persist(pizza);
        entityManager.persist(sushi);

        Restaurant openPizzaPlace = restaurant("Pizza Place", RestaurantApprovalStatus.APPROVED, true, Set.of(pizza));
        Restaurant closedPizzaPlace = restaurant("Closed Pizza", RestaurantApprovalStatus.APPROVED, false, Set.of(pizza));
        Restaurant pendingPizzaPlace = restaurant("Pending Pizza", RestaurantApprovalStatus.PENDING, true, Set.of(pizza));
        Restaurant approvedSushiPlace = restaurant("Sushi Place", RestaurantApprovalStatus.APPROVED, true, Set.of(sushi));

        entityManager.persist(openPizzaPlace);
        entityManager.persist(closedPizzaPlace);
        entityManager.persist(pendingPizzaPlace);
        entityManager.persist(approvedSushiPlace);
        entityManager.flush();
        entityManager.clear();

        Specification<Restaurant> spec = Specification
                .where(RestaurantSpecifications.approvedForCustomerListing())
                .and(RestaurantSpecifications.hasCategorySlug(pizzaSlug));

        List<Restaurant> results = restaurantRepository
                .findAll(spec, PageRequest.of(0, 10, Sort.by("id")))
                .getContent();

        // APPROVED + tagged 'pizza' — the closed-but-approved one is now kept (flagged, not hidden);
        // the PENDING one and the wrong-category one are excluded.
        assertThat(results).extracting(Restaurant::getName)
                .containsExactlyInAnyOrder("Pizza Place", "Closed Pizza");

        List<RestaurantCategorySlug> slugRows = restaurantRepository.findCategorySlugsByRestaurantIds(
                results.stream().map(Restaurant::getId).toList());
        assertThat(slugRows).extracting(RestaurantCategorySlug::slug).containsOnly(pizzaSlug);
    }

    @Test
    void findAll_withApprovalStatusSpecification_returnsOnlyMatchingStatus() {
        Restaurant pending = restaurant("Pending Place-" + System.nanoTime(), RestaurantApprovalStatus.PENDING, true, Set.of());
        Restaurant approved = restaurant("Approved Place-" + System.nanoTime(), RestaurantApprovalStatus.APPROVED, true, Set.of());
        Restaurant suspended = restaurant("Suspended Place-" + System.nanoTime(), RestaurantApprovalStatus.SUSPENDED, true, Set.of());

        entityManager.persist(pending);
        entityManager.persist(approved);
        entityManager.persist(suspended);
        entityManager.flush();
        entityManager.clear();

        List<Restaurant> results = restaurantRepository
                .findAll(RestaurantSpecifications.hasApprovalStatus(RestaurantApprovalStatus.SUSPENDED),
                        PageRequest.of(0, 10, Sort.by("id")))
                .getContent();

        assertThat(results).extracting(Restaurant::getName).containsExactly(suspended.getName());
    }

    @Test
    void findByIdWithCategories_loadsCategoriesWithoutLazyInitializationException() {
        Category pizza = category("بيتزا-" + System.nanoTime(), "pizza-" + System.nanoTime());
        entityManager.persist(pizza);
        Restaurant restaurant = restaurant("Pizza Place", RestaurantApprovalStatus.APPROVED, true, Set.of(pizza));
        entityManager.persist(restaurant);
        entityManager.flush();
        entityManager.clear();

        Optional<Restaurant> found = restaurantRepository.findByIdWithCategories(restaurant.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCategories()).extracting(Category::getName).containsExactly(pizza.getName());
    }

    @Test
    void countByApprovalStatusAndCreatedAtLessThan_countsApprovedRestaurants_createdBeforeBoundary() {
        Restaurant approvedBefore = restaurant("Approved Before-" + System.nanoTime(), RestaurantApprovalStatus.APPROVED, true, Set.of());
        Restaurant approvedAfter = restaurant("Approved After-" + System.nanoTime(), RestaurantApprovalStatus.APPROVED, true, Set.of());
        Restaurant pendingBefore = restaurant("Pending Before-" + System.nanoTime(), RestaurantApprovalStatus.PENDING, true, Set.of());
        entityManager.persist(approvedBefore);
        entityManager.persist(approvedAfter);
        entityManager.persist(pendingBefore);
        entityManager.flush();
        setCreatedAt(approvedBefore, LocalDateTime.of(2026, 8, 1, 0, 0));
        setCreatedAt(approvedAfter, LocalDateTime.of(2026, 8, 20, 0, 0));
        setCreatedAt(pendingBefore, LocalDateTime.of(2026, 8, 1, 0, 0));
        entityManager.clear();

        long count = restaurantRepository.countByApprovalStatusAndCreatedAtLessThan(
                RestaurantApprovalStatus.APPROVED, LocalDateTime.of(2026, 8, 10, 0, 0));

        assertThat(count).isEqualTo(1);
    }

    /** {@code createdAt} is {@code @CreationTimestamp}-generated, so boundary tests must overwrite it directly. */
    private void setCreatedAt(Restaurant restaurant, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE restaurants SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", restaurant.getId())
                .executeUpdate();
    }

    private Category category(String name, String slug) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setIcon("icon");
        return category;
    }

    private Restaurant restaurant(String name, RestaurantApprovalStatus status, boolean open, Set<Category> categories) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setCuisine(name);
        restaurant.setDeliveryFee(BigDecimal.valueOf(10));
        restaurant.setMinimumOrder(BigDecimal.valueOf(30));
        restaurant.setEstimatedDeliveryMinMinutes(20);
        restaurant.setEstimatedDeliveryMaxMinutes(30);
        restaurant.setApprovalStatus(status);
        restaurant.setOpenForOrders(open);
        restaurant.setCategories(categories);
        return restaurant;
    }
}
