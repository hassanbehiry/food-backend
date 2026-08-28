package com.food.foodapp.cart.entity;

import com.food.foodapp.menu.entity.MenuItem;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One line of a {@link Cart}. Deliberately carries no price — every price shown to
 * the customer is always re-derived live from {@link MenuItem#getPrice()} at read
 * time, per the task's "current server-side price is used" rule, so a menu price
 * change can never leave a cart showing a stale figure.
 * <p>
 * {@code (cart_id, menu_item_id)} is unique so the same menu item can never appear as
 * two rows in one cart — merges/syncs must update the existing row instead of
 * inserting a second one. {@code menuItem} cascades on delete: if an owner hard-deletes
 * a menu item, any cart lines referencing it are removed by the database itself rather
 * than leaving a dangling reference or failing the delete with a foreign-key error.
 */
@Entity
@Table(name = "cart_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "menu_item_id"}))
@Check(constraints = "quantity > 0 AND quantity <= " + CartItem.MAX_QUANTITY_PER_ITEM)
@Getter
@Setter
@NoArgsConstructor
public class CartItem {

    /** Upper bound on a single line's quantity — no configured limit existed in the codebase; chosen as a sane anti-abuse default. */
    public static final int MAX_QUANTITY_PER_ITEM = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MenuItem menuItem;

    @Column(nullable = false)
    private int quantity;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
