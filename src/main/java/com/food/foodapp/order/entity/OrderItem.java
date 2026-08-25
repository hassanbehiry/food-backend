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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

/**
 * One line of a placed {@link Order}, snapshotted at order-creation time. {@code menuItemId} is
 * a plain informational column, not a managed {@code @ManyToOne} relation to {@code MenuItem}:
 * an order receipt must stay intact and correctly priced even after the referenced menu item is
 * later edited or hard-deleted, so nothing here may be re-derived from — or block deletion of —
 * the live menu item.
 */
@Entity
@Table(name = "order_items")
@Check(constraints = "quantity > 0 AND unit_price >= 0 AND line_total >= 0")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "menu_item_id")
    private Long menuItemId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;
}
