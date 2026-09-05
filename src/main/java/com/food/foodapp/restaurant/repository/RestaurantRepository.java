package com.food.foodapp.restaurant.repository;

import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, JpaSpecificationExecutor<Restaurant> {

    @Query("SELECT r FROM Restaurant r LEFT JOIN FETCH r.categories WHERE r.id = :id")
    Optional<Restaurant> findByIdWithCategories(@Param("id") Long id);

    /**
     * The authenticated owner's restaurant. Owner registration creates exactly one restaurant per
     * owner, so this resolves "my restaurant" for the no-argument owner dashboard without the
     * client passing a restaurant id.
     */
    Optional<Restaurant> findByOwnerId(Long ownerId);

    /**
     * Category slugs for a page of restaurants, as flat (restaurant id, slug) rows — one small
     * query for the whole page, so the list response's {@code categoryIds} field never triggers
     * an N+1 and the to-many join never multiplies the paged restaurant rows themselves.
     * Restaurants with no category tag simply have no row here.
     */
    @Query("SELECT new com.food.foodapp.restaurant.repository.RestaurantCategorySlug(r.id, c.slug) "
            + "FROM Restaurant r JOIN r.categories c WHERE r.id IN :restaurantIds")
    List<RestaurantCategorySlug> findCategorySlugsByRestaurantIds(@Param("restaurantIds") List<Long> restaurantIds);

    /**
     * The admin analytics overview's "active restaurants" KPI: restaurants whose <b>current</b>
     * {@code approvalStatus} matches, created before {@code boundary}. This is an approximation
     * of "how many were active as of that date" rather than a true historical snapshot — the
     * codebase has no persisted approval-status history (see
     * {@code RestaurantApprovalStatus}'s "no audit log" note), so a restaurant that was
     * {@code APPROVED} as of {@code boundary} but has since been suspended is not counted for
     * {@code boundary} either, even though it genuinely was active then. Called with two
     * different {@code boundary} values (period end and period start) to build the KPI's
     * period-over-period trend.
     */
    long countByApprovalStatusAndCreatedAtLessThan(RestaurantApprovalStatus approvalStatus, LocalDateTime boundary);
}
