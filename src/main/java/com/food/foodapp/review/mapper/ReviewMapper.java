package com.food.foodapp.review.mapper;

import com.food.foodapp.review.dto.ReviewResponse;
import com.food.foodapp.review.dto.ReviewTestimonialResponse;
import com.food.foodapp.review.entity.Review;

/**
 * Stateless {@link Review} &rarr; DTO mapping. Static-method style, matching
 * {@code FavoriteMapper} / {@code OrderMapper}.
 */
public final class ReviewMapper {

    private ReviewMapper() {
    }

    /**
     * {@code name} &larr; customer name, {@code role} &larr; order delivery city, {@code text}
     * &larr; comment. Callers must load {@code customer} and {@code order} (the testimonials query
     * join-fetches both).
     */
    public static ReviewTestimonialResponse toTestimonial(Review review) {
        return ReviewTestimonialResponse.builder()
                .name(review.getCustomer().getName())
                .role(review.getOrder().getDeliveryCity())
                .rating(review.getRating())
                .text(review.getComment())
                .build();
    }

    /** Only touches {@code order}/{@code restaurant} identifiers, so it is safe to call on a review whose associations are lazy proxies. */
    public static ReviewResponse toResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .orderId(review.getOrder().getId())
                .restaurantId(review.getRestaurant().getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
