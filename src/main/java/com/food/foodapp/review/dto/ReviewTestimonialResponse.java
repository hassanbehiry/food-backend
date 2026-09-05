package com.food.foodapp.review.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One homepage testimonial. Matches the frontend's {@code getReviews()} item shape
 * {@code {name, role, rating, text}} field-for-field:
 * <ul>
 *   <li>{@code name} &larr; the reviewing customer's display name</li>
 *   <li>{@code role} &larr; the order's {@code deliveryCity} (there is no richer "role" in the model)</li>
 *   <li>{@code rating} &larr; the 1..5 star rating, as a JSON number</li>
 *   <li>{@code text} &larr; the review comment (always present — the query filters out null comments)</li>
 * </ul>
 */
@Getter
@Builder
public class ReviewTestimonialResponse {

    private String name;

    private String role;

    private int rating;

    private String text;
}
