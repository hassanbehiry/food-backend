package com.food.foodapp.review;

import com.food.foodapp.auth.entity.Role;
import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.repository.UserRepository;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.order.entity.OrderStatus;
import com.food.foodapp.order.entity.PaymentMethod;
import com.food.foodapp.order.repository.OrderRepository;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import com.food.foodapp.restaurant.repository.RestaurantRepository;
import com.food.foodapp.review.entity.Review;
import com.food.foodapp.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-level coverage for the reviews domain (BACKEND-005). Runs against the real Postgres the rest
 * of the suite uses ({@code spring-boot-docker-compose} on the orchestrator's regression run, or a
 * {@code SPRING_DATASOURCE_URL} override locally) so Flyway V5 + Hibernate {@code validate} + the
 * rating-aggregation bulk update all exercise the actual database. {@code @Transactional} rolls
 * every test back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @MockitoBean
    private UserContext userContext;

    private Long customerId;
    private Long otherCustomerId;
    private Long restaurantId;
    private Long deliveredOrderId;
    private Long pendingOrderId;

    @BeforeEach
    void setUp() {
        User customer = persistUser("review-customer-" + System.nanoTime() + "@example.com", "Sara Ahmed");
        User otherCustomer = persistUser("review-other-" + System.nanoTime() + "@example.com", "Omar Ali");
        Restaurant restaurant = persistRestaurant("Reviews Test Kitchen");

        customerId = customer.getId();
        otherCustomerId = otherCustomer.getId();
        restaurantId = restaurant.getId();
        deliveredOrderId = persistOrder(customer, restaurant, OrderStatus.DELIVERED).getId();
        pendingOrderId = persistOrder(customer, restaurant, OrderStatus.NEW).getId();

        when(userContext.getCurrentUserId()).thenReturn(customerId);
    }

    @Test
    void getReviews_returnsEmptyArray_whenNoReviewsExist() throws Exception {
        mockMvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getReviews_returnsTestimonialShape() throws Exception {
        persistReview(deliveredOrderId, 5, "Amazing food, fast delivery!");

        mockMvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Sara Ahmed"))
                .andExpect(jsonPath("$[0].role").value("Cairo"))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].text").value("Amazing food, fast delivery!"));
    }

    @Test
    void createReview_returns201_andMovesRestaurantRating() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/reviews", deliveredOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"comment\":\"Solid meal\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.orderId").value(deliveredOrderId))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.comment").value("Solid meal"));

        Restaurant updated = restaurantRepository.findById(restaurantId).orElseThrow();
        assertThat(updated.getRatingAverage()).isEqualByComparingTo("4.00");
        assertThat(updated.getReviewCount()).isEqualTo(1);
    }

    @Test
    void createReview_returns409_whenOrderAlreadyReviewed() throws Exception {
        persistReview(deliveredOrderId, 5, "First and only review");

        mockMvc.perform(post("/api/v1/orders/{orderId}/reviews", deliveredOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":3,\"comment\":\"second attempt\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createReview_returns403_whenOrderBelongsToAnotherCustomer() throws Exception {
        when(userContext.getCurrentUserId()).thenReturn(otherCustomerId);

        mockMvc.perform(post("/api/v1/orders/{orderId}/reviews", deliveredOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"not my order\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReview_returns409_whenOrderNotDelivered() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/reviews", pendingOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"too early\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createReview_returns400_whenRatingOutOfRange() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/reviews", deliveredOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":9}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReview_returns404_whenOrderDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/reviews", 99_999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"ghost order\"}"))
                .andExpect(status().isNotFound());
    }

    private User persistUser(String email, String name) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setRole(Role.CUSTOMER);
        return userRepository.save(user);
    }

    private Restaurant persistRestaurant(String name) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setCuisine(name);
        restaurant.setDeliveryFee(BigDecimal.valueOf(10));
        restaurant.setMinimumOrder(BigDecimal.valueOf(30));
        restaurant.setEstimatedDeliveryMinMinutes(20);
        restaurant.setEstimatedDeliveryMaxMinutes(40);
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        restaurant.setOpenForOrders(true);
        return restaurantRepository.save(restaurant);
    }

    private Order persistOrder(User customer, Restaurant restaurant, OrderStatus status) {
        Order order = new Order();
        order.setOrderNumber("ORD-TEST-" + System.nanoTime());
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setDeliveryStreet("1 Test Street");
        order.setDeliveryCity("Cairo");
        order.setSubtotal(BigDecimal.valueOf(100));
        order.setDeliveryFee(BigDecimal.valueOf(10));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.valueOf(110));
        order.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        order.setStatus(status);
        return orderRepository.save(order);
    }

    private void persistReview(Long orderId, int rating, String comment) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        Review review = new Review();
        review.setOrder(order);
        review.setRestaurant(order.getRestaurant());
        review.setCustomer(order.getCustomer());
        review.setRating(rating);
        review.setComment(comment);
        reviewRepository.save(review);
    }
}
