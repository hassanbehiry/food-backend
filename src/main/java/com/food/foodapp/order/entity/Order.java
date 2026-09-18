package com.food.foodapp.order.entity;

import com.food.foodapp.auth.entity.User;
import com.food.foodapp.restaurant.entity.Restaurant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An immutable record of a placed order. {@code subtotal}, {@code deliveryFee}, {@code total},
 * and every {@link OrderItem}'s own {@code unitPrice}/
 * {@code lineTotal} are snapshots computed once at order-creation time from then-authoritative
 * data and never recalculated afterward, so a later menu-price change can never alter a past
 * receipt.
 * <p>
 * The delivery address is snapshotted the same way, as flat {@code delivery*} fields rather than
 * a live {@code Address} relation: {@code AddressService} lets a customer edit or hard-delete a
 * saved address, and neither should be able to retroactively change where a past order says it
 * was delivered, or be blocked by a past order still referencing it.
 */
@Entity
@Table(name = "orders")
@Check(constraints = "subtotal >= 0 AND delivery_fee >= 0 AND total >= 0")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    /**
     * Cascades on persist only: unlike {@code Cart.items}, order items are written once at
     * creation and never mutated afterward, so there's no need for the repository-managed
     * mutation pattern {@code CartItem} uses.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "delivery_label", length = 100)
    private String deliveryLabel;

    @Column(name = "delivery_street", nullable = false, length = 255)
    private String deliveryStreet;

    @Column(name = "delivery_city", nullable = false, length = 100)
    private String deliveryCity;

    @Column(name = "delivery_postal_code", length = 20)
    private String deliveryPostalCode;

    @Column(name = "delivery_notes", length = 500)
    private String deliveryNotes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.CONFIRMED;

    /**
     * Set once, the moment the order reaches {@link OrderStatus#DELIVERED} — via either the
     * customer confirming receipt ({@code PUT /orders/{id}/confirm-delivery}) or the restaurant
     * confirming hand-off ({@code POST /orders/{id}/deliver}); see {@link #deliveredBy}.
     * {@code null} until then.
     */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** Which side made that delivery confirmation — {@code null} until {@link #deliveredAt} is set. */
    @Enumerated(EnumType.STRING)
    @Column(name = "delivered_by", length = 20)
    private DeliveryConfirmedBy deliveredBy;

    /**
     * Set once, the moment the restaurant dispatches the order to a courier
     * ({@code OrderService#sendToDelivery}, {@code OUT_FOR_DELIVERY}) — {@code null} until then.
     */
    @Column(name = "sent_to_delivery_at")
    private LocalDateTime sentToDeliveryAt;

    /**
     * Free-text name of whoever is carrying the order, optionally supplied by the restaurant when
     * dispatching it (see {@code OrderService#sendToDelivery}). There is no dedicated courier/driver
     * account or role in this system (see {@code Role}), so this is a plain label rather than a
     * relation — {@code null} if the restaurant didn't provide one.
     */
    @Column(name = "delivery_person_name", length = 150)
    private String deliveryPersonName;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
