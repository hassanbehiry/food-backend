package com.food.foodapp.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Platform-wide cuisine/food-type taxonomy used as homepage discovery chips
 * (e.g. "بيتزا", "برجر", "سوشي"). Distinct from a restaurant's own free-text
 * cuisine label and from the per-restaurant menu category/tab.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Stable, URL-safe identifier the frontend keys categories by (homepage chip URL param,
     * dashboard restaurant filter). Distinct from the numeric {@link #id}. Added in V4.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    /**
     * Either a FontAwesome solid-style class token (e.g. {@code fa-pizza-slice}) or an
     * {@code http(s)://} image URL — the frontend renders whichever it is. Widened from 255 to
     * 500 in V15 to comfortably hold a URL, matching {@code Restaurant.logoUrl}/{@code coverImageUrl}.
     */
    @Column(nullable = false, length = 500)
    private String icon;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
