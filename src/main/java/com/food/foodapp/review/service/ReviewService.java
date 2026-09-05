package com.food.foodapp.review.service;

import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.common.exception.DuplicateReviewException;
import com.food.foodapp.common.exception.OrderNotFoundException;
import com.food.foodapp.common.exception.ReviewAccessDeniedException;
import com.food.foodapp.common.exception.ReviewNotEligibleException;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.repository.OrderRepository;
import com.food.foodapp.review.dto.ReviewCreateRequest;
import com.food.foodapp.review.dto.ReviewResponse;
import com.food.foodapp.review.dto.ReviewTestimonialResponse;
import com.food.foodapp.review.entity.Review;
import com.food.foodapp.review.mapper.ReviewMapper;
import com.food.foodapp.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reviews &amp; ratings.
 * <p>
 * {@link #getTopTestimonials()} feeds the public homepage strip and needs no caller identity.
 * {@link #createReview} is the single write path for a {@link Review}: it resolves the caller via
 * {@link UserContext} (never from the request), and in one transaction validates
 * order-existence &rarr; ownership &rarr; delivered-status &rarr; not-already-reviewed, persists the
 * review, then recomputes the restaurant's {@code ratingAverage}/{@code reviewCount} from the
 * {@code reviews} table via {@link ReviewRepository#recalculateRestaurantRating} — so the order and
 * restaurant domains carry no review code.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserContext userContext;

    @Value("${app.reviews.testimonials.min-rating:4}")
    private int testimonialMinRating;

    @Value("${app.reviews.testimonials.limit:12}")
    private int testimonialLimit;

    @Transactional(readOnly = true)
    public List<ReviewTestimonialResponse> getTopTestimonials() {
        return reviewRepository
                .findTopTestimonials(testimonialMinRating, PageRequest.of(0, testimonialLimit))
                .stream()
                .map(ReviewMapper::toTestimonial)
                .toList();
    }

    @Transactional
    public ReviewResponse createReview(Long orderId, ReviewCreateRequest request) {
        Long currentUserId = userContext.getCurrentUserId();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (!order.getCustomer().getId().equals(currentUserId)) {
            throw new ReviewAccessDeniedException("Order " + orderId + " does not belong to the current user");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ReviewNotEligibleException(
                    "Order " + orderId + " cannot be reviewed until it has been delivered");
        }
        if (reviewRepository.existsByOrderId(orderId)) {
            throw new DuplicateReviewException("Order " + orderId + " has already been reviewed");
        }

        Long restaurantId = order.getRestaurant().getId();

        Review review = new Review();
        review.setOrder(order);
        review.setRestaurant(order.getRestaurant());
        review.setCustomer(order.getCustomer());
        review.setRating(request.getRating());
        review.setComment(normalizeComment(request.getComment()));

        Review saved = reviewRepository.saveAndFlush(review);
        // Build the response before the recompute clears the persistence context.
        ReviewResponse response = ReviewMapper.toResponse(saved);

        reviewRepository.recalculateRestaurantRating(restaurantId);

        return response;
    }

    /** Treat a blank/whitespace-only comment as "no comment" so it never reaches the testimonials strip. */
    private static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
