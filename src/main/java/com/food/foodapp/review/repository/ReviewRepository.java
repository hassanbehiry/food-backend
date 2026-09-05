package com.food.foodapp.review.repository;

import com.food.foodapp.review.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** One-review-per-order guard for the create path (backed by the {@code uk_reviews_order_id} constraint). */
    boolean existsByOrderId(Long orderId);

    /**
     * The homepage testimonials strip: the highest-rated reviews that actually carry written text,
     * newest-first among equal ratings, capped by {@code pageable}. Returns a bare {@code List}
     * (not a pagination envelope) to match the frontend's {@code getReviews()} bare-array contract.
     * Join-fetches the to-one {@code customer} and {@code order} the mapper reads for {@code name}
     * and {@code role} — safe to combine with {@code Pageable} because both are to-one.
     * On a database with no reviews yet this simply returns an empty list.
     */
    @Query("SELECT r FROM Review r "
            + "JOIN FETCH r.customer "
            + "JOIN FETCH r.order "
            + "WHERE r.comment IS NOT NULL AND r.rating >= :minRating "
            + "ORDER BY r.rating DESC, r.createdAt DESC")
    List<Review> findTopTestimonials(@Param("minRating") int minRating, Pageable pageable);

    /**
     * Recomputes and persists one restaurant's {@code ratingAverage} / {@code reviewCount} straight
     * from the {@code reviews} table, inside the caller's transaction. Lives here rather than in
     * {@code RestaurantService} so the review write path never has to reach into the restaurant
     * domain to mutate it.
     * <p>
     * {@code flushAutomatically} pushes the just-persisted {@link Review} to the database before the
     * recompute reads it; {@code clearAutomatically} then evicts the now-stale {@code Restaurant}
     * from the persistence context so a read-back in the same transaction sees the new values.
     * {@code COALESCE(AVG(...), 0)} keeps the average at {@code 0} if the last review for a
     * restaurant is ever removed. {@code numeric(3,2)} comfortably holds an average of ratings that
     * are themselves 1..5.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Restaurant r SET "
            + "r.ratingAverage = (SELECT COALESCE(AVG(rv.rating), 0) FROM Review rv WHERE rv.restaurant.id = :restaurantId), "
            + "r.reviewCount = (SELECT COUNT(rv) FROM Review rv WHERE rv.restaurant.id = :restaurantId) "
            + "WHERE r.id = :restaurantId")
    void recalculateRestaurantRating(@Param("restaurantId") Long restaurantId);
}
