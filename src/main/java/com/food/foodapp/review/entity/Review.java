package com.food.foodapp.review.entity;

import com.food.foodapp.auth.entity.User;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.restaurant.entity.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A customer's review of a single {@link Order} they placed and that was delivered.
 * <p>
 * The unique constraint on {@code order_id} is the real one-review-per-order guarantee;
 * {@code ReviewService} additionally checks {@code existsByOrderId} first so a duplicate surfaces
 * as a clean {@code 409} rather than a constraint-violation {@code 500}. {@code restaurant} and
 * {@code customer} are denormalized copies of the order's own restaurant/customer — carried
 * directly on the review so the rating-aggregation query
 * ({@code ReviewRepository#recalculateRestaurantRating}) and the testimonials query never have to
 * hop through {@code Order}, and so a review stays attributable even if the order graph changes.
 * <p>
 * {@code rating} is constrained to 1..5 both here (request-level {@code @Min/@Max}) and at the
 * database ({@code reviews_rating_check}). {@code comment} is optional; the homepage testimonials
 * strip only ever surfaces reviews that have one.
 */
@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(name = "uk_reviews_order_id", columnNames = "order_id"))
@Check(constraints = "rating >= 1 AND rating <= 5")
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false, updatable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private User customer;

    @Column(nullable = false)
    private int rating;

    @Column(length = 2000)
    private String comment;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
