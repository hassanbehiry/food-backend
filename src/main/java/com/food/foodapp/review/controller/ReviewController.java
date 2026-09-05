package com.food.foodapp.review.controller;

import com.food.foodapp.review.dto.ReviewCreateRequest;
import com.food.foodapp.review.dto.ReviewResponse;
import com.food.foodapp.review.dto.ReviewTestimonialResponse;
import com.food.foodapp.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reviews &amp; ratings endpoints.
 * <ul>
 *   <li>{@code GET /api/v1/reviews} — public (permitAll); homepage testimonials strip.</li>
 *   <li>{@code POST /api/v1/orders/{orderId}/reviews} — authenticated; the caller is resolved from
 *       {@code UserContext} inside {@link ReviewService}, never taken from the request.</li>
 * </ul>
 * The create route is the only write path for a review and lives here rather than in
 * {@code OrderController} on purpose, so the order domain carries no review code. No class-level
 * {@code @RequestMapping} because the two routes sit under different base paths.
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** GET /api/v1/reviews */
    @GetMapping("/api/v1/reviews")
    public ResponseEntity<List<ReviewTestimonialResponse>> listTestimonials() {
        return ResponseEntity.ok(reviewService.getTopTestimonials());
    }

    /** POST /api/v1/orders/{orderId}/reviews */
    @PostMapping("/api/v1/orders/{orderId}/reviews")
    public ResponseEntity<ReviewResponse> createReview(@PathVariable Long orderId,
                                                       @Valid @RequestBody ReviewCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(orderId, request));
    }
}
