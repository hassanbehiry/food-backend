package com.food.foodapp.coupon.service;

import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.cart.dto.CartResponse;
import com.food.foodapp.cart.entity.Cart;
import com.food.foodapp.cart.mapper.CartMapper;
import com.food.foodapp.cart.repository.CartRepository;
import com.food.foodapp.common.exception.CartEmptyException;
import com.food.foodapp.common.exception.CouponNotApplicableException;
import com.food.foodapp.common.exception.CouponNotFoundException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.coupon.entity.Coupon;
import com.food.foodapp.coupon.entity.CouponUsage;
import com.food.foodapp.coupon.entity.DiscountType;
import com.food.foodapp.coupon.repository.CouponRepository;
import com.food.foodapp.coupon.repository.CouponUsageRepository;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * The authoritative coupon-validation and discount-calculation engine. Nothing here ever trusts
 * a discount amount from the caller — every call recomputes it from the coupon's persisted rules
 * against a subtotal the caller must have already derived server-side (see
 * {@code OrderService#computeOrder}, which is the only place that discount feeds into a total).
 * <p>
 * {@link #validate} is a plain read (safe under a read-only transaction) reused by both the
 * checkout preview and order-placement paths, so a coupon is re-validated from scratch at both
 * steps rather than trusting whatever the preview call decided. {@link #recordUsage} is the
 * separate, write-locked commit step called only once an order has actually been persisted — see
 * its own javadoc for why usage-limit enforcement can't happen inside {@link #validate} itself.
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final CartRepository cartRepository;
    private final UserContext userContext;

    /**
     * The standalone {@code POST /coupons/validate} preview: resolves the caller's own standing
     * cart via {@link UserContext} and validates {@code rawCode} against it, without persisting
     * anything. Reuses {@link CartMapper#toResponse} for the subtotal/delivery-fee arithmetic so
     * it always agrees with what {@code GET /cart} itself would show.
     */
    @Transactional(readOnly = true)
    public CouponApplicationPreview validateForCurrentCart(String rawCode) {
        Long customerId = userContext.getCurrentUserId();
        Cart cart = cartRepository.findByCustomerIdWithItems(customerId).orElse(null);
        if (cart == null || cart.getItems().isEmpty()) {
            throw new CartEmptyException("Cart is empty");
        }
        Restaurant restaurant = cart.getRestaurant();
        if (restaurant == null || !RestaurantService.isCustomerVisible(restaurant)) {
            throw new RestaurantNotFoundException("Restaurant not found");
        }

        CartResponse cartResponse = CartMapper.toResponse(cart);
        CouponApplication application = validate(rawCode, restaurant, cartResponse.getSubtotal());
        return new CouponApplicationPreview(application, cartResponse.getSubtotal(), cartResponse.getDeliveryFee());
    }

    /**
     * Validates {@code rawCode} against the given restaurant/subtotal context and returns the
     * resolved coupon plus the discount it currently yields. Throws {@link CouponNotFoundException}
     * if the code doesn't exist at all, or {@link CouponNotApplicableException} if it exists but
     * can't be used right now (inactive, outside its validity window, below the minimum order,
     * scoped to a different restaurant, or its usage limit has already been reached).
     * <p>
     * The usage-limit check here is a soft, unlocked count — good enough to reject an
     * already-exhausted coupon during preview, but not sufficient on its own to guarantee the
     * limit is never exceeded under concurrent checkouts; {@link #recordUsage} re-checks it under
     * a row lock at the one point that actually matters.
     */
    @Transactional(readOnly = true)
    public CouponApplication validate(String rawCode, Restaurant restaurant, BigDecimal subtotal) {
        String code = normalizeCode(rawCode);
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + rawCode));

        requireApplicable(coupon, restaurant, subtotal);

        BigDecimal discount = computeDiscount(coupon, subtotal);
        return new CouponApplication(coupon, discount);
    }

    /**
     * Re-locks the coupon row and re-checks its usage limit before inserting the {@link CouponUsage}
     * record, so two checkouts racing to redeem the last remaining use of a limited coupon
     * serialize instead of both succeeding. Must only be called from within the same write
     * transaction that just persisted {@code order} (see {@code OrderService#placeOrder}) — a
     * failure here rolls back that order along with it, exactly like any other late-discovered
     * checkout-time conflict.
     */
    @Transactional
    public void recordUsage(Coupon coupon, Order order) {
        Coupon locked = couponRepository.findByIdForUpdate(coupon.getId())
                .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + coupon.getCode()));
        if (locked.getUsageLimit() != null && couponUsageRepository.countByCouponId(locked.getId()) >= locked.getUsageLimit()) {
            throw new CouponNotApplicableException("Coupon usage limit has been reached: " + locked.getCode());
        }
        User customer = order.getCustomer();
        couponUsageRepository.save(new CouponUsage(locked, order, customer));
    }

    private void requireApplicable(Coupon coupon, Restaurant restaurant, BigDecimal subtotal) {
        if (!coupon.isActive()) {
            throw new CouponNotApplicableException("Coupon is not active: " + coupon.getCode());
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            throw new CouponNotApplicableException("Coupon is not yet valid: " + coupon.getCode());
        }
        if (coupon.getEndsAt() != null && now.isAfter(coupon.getEndsAt())) {
            throw new CouponNotApplicableException("Coupon has expired: " + coupon.getCode());
        }
        if (coupon.getRestaurant() != null && !coupon.getRestaurant().getId().equals(restaurant.getId())) {
            throw new CouponNotApplicableException("Coupon is not valid for this restaurant: " + coupon.getCode());
        }
        if (coupon.getMinOrderAmount() != null && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new CouponNotApplicableException(
                    "Order subtotal must be at least " + coupon.getMinOrderAmount() + " to use coupon " + coupon.getCode());
        }
        if (coupon.getUsageLimit() != null && couponUsageRepository.countByCouponId(coupon.getId()) >= coupon.getUsageLimit()) {
            throw new CouponNotApplicableException("Coupon usage limit has been reached: " + coupon.getCode());
        }
    }

    /** Never returns more than {@code subtotal}, so a misconfigured coupon can never discount the delivery fee away. */
    private BigDecimal computeDiscount(Coupon coupon, BigDecimal subtotal) {
        BigDecimal raw = coupon.getDiscountType() == DiscountType.PERCENTAGE
                ? subtotal.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : coupon.getDiscountValue();
        if (coupon.getMaxDiscountAmount() != null) {
            raw = raw.min(coupon.getMaxDiscountAmount());
        }
        return raw.min(subtotal).max(BigDecimal.ZERO);
    }

    private String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new CouponNotFoundException("Coupon code is required");
        }
        return rawCode.trim().toUpperCase();
    }

    public record CouponApplication(Coupon coupon, BigDecimal discount) {
    }

    public record CouponApplicationPreview(CouponApplication application, BigDecimal subtotal, BigDecimal deliveryFee) {
    }
}
