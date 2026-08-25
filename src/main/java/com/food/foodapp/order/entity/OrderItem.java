package com.food.foodapp.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

/**
 * One immutable line of a placed {@link Order}, snapshotted once at order-creation time via the
 * constructor below and never modified afterward — every field except {@code id} is mapped
 * {@code updatable = false}, so even a future bug that calls a persistence-managed setter can
 * never rewrite a receipt line or re-parent it onto a different order.
 * <p>
 * {@code menuItemId} and {@code imageUrl} are plain informational columns, not a managed
 * {@code @ManyToOne} relation to {@code MenuItem}: an order receipt must stay intact and
 * correctly priced/labeled even after the referenced menu item is later edited or hard-deleted,
 * so nothing here may be re-derived from — or block deletion of — the live menu item.
 * {@code menuItemId} is still recorded {@code NOT NULL}: {@link com.food.foodapp.order.service.OrderService}
 * always builds an order item from a cart line that references a real menu item, so an order item
 * with no menu-item reference would indicate a bug rather than a legitimate state.
 */
@Entity
@Table(name = "order_items")
@Check(constraints = "quantity > 0 AND unit_price >= 0 AND line_total >= 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "menu_item_id", nullable = false, updatable = false)
    private Long menuItemId;

    @Column(nullable = false, length = 150, updatable = false)
    private String name;

    @Column(name = "image_url", length = 500, updatable = false)
    private String imageUrl;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal lineTotal;

    /** The only way to build an order item: every field is required and fixed for the item's lifetime. */
    public OrderItem(Order order, Long menuItemId, String name, String imageUrl, BigDecimal unitPrice,
                      int quantity, BigDecimal lineTotal) {
        this.order = order;
        this.menuItemId = menuItemId;
        this.name = name;
        this.imageUrl = imageUrl;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = lineTotal;
    }
}
