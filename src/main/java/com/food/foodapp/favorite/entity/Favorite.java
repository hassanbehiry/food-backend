package com.food.foodapp.favorite.entity;

import com.food.foodapp.auth.entity.User;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A customer's bookmark of a restaurant. The unique constraint on
 * ({@code customer_id}, {@code restaurant_id}) is the actual duplicate-prevention
 * mechanism; {@code FavoriteService#toggleFavorite} additionally locks the customer's
 * user row before checking for an existing row, so the constraint is a persistence-level
 * safety net rather than the primary defense — matching how {@code AddressService}
 * protects its own per-customer invariant. Deliberately has no reference to restaurant
 * availability: a favorite is never removed just because its restaurant later becomes
 * unavailable, since the customer might still want it back once the restaurant reopens.
 * {@code FavoriteMapper} computes current availability at read time instead.
 */
@Entity
@Table(name = "favorites", uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "restaurant_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false, updatable = false)
    private Restaurant restaurant;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
