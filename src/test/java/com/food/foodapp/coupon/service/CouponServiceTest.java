package com.food.foodapp.coupon.service;

import com.food.foodapp.auth.entity.User;
import com.food.foodapp.auth.security.UserContext;
import com.food.foodapp.cart.entity.Cart;
import com.food.foodapp.cart.entity.CartItem;
import com.food.foodapp.cart.repository.CartRepository;
import com.food.foodapp.common.exception.CartEmptyException;
import com.food.foodapp.common.exception.CouponNotApplicableException;
import com.food.foodapp.common.exception.CouponNotFoundException;
import com.food.foodapp.common.exception.RestaurantNotFoundException;
import com.food.foodapp.coupon.entity.Coupon;
import com.food.foodapp.coupon.entity.DiscountType;
import com.food.foodapp.coupon.repository.CouponRepository;
import com.food.foodapp.coupon.repository.CouponUsageRepository;
import com.food.foodapp.menu.entity.MenuItem;
import com.food.foodapp.order.entity.Order;
import com.food.foodapp.restaurant.entity.Restaurant;
import com.food.foodapp.restaurant.entity.RestaurantApprovalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private UserContext userContext;

    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponService = new CouponService(couponRepository, couponUsageRepository, cartRepository, userContext);
        lenient().when(userContext.getCurrentUserId()).thenReturn(1L);
    }

    @Test
    void validate_throwsCouponNotFound_whenCodeDoesNotExist() {
        when(couponRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.validate("missing", restaurant(5L), BigDecimal.valueOf(100)))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void validate_throwsCouponNotFound_whenCodeIsBlank() {
        assertThatThrownBy(() -> couponService.validate("   ", restaurant(5L), BigDecimal.valueOf(100)))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void validate_normalizesCode_toUpperCaseTrimmed_beforeLookup() {
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        CouponService.CouponApplication application = couponService.validate("  save10  ", restaurant(5L), BigDecimal.valueOf(100));

        assertThat(application.coupon().getCode()).isEqualTo("SAVE10");
    }

    @Test
    void validate_throwsCouponNotApplicable_whenInactive() {
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        coupon.setActive(false);
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate("SAVE10", restaurant(5L), BigDecimal.valueOf(100)))
                .isInstanceOf(CouponNotApplicableException.class);
    }

    @Test
    void validate_throwsCouponNotApplicable_whenNotYetStarted() {
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        coupon.setStartsAt(LocalDateTime.now().plusDays(1));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate("SAVE10", restaurant(5L), BigDecimal.valueOf(100)))
                .isInstanceOf(CouponNotApplicableException.class);
    }

    @Test
    void validate_throwsCouponNotApplicable_whenExpired() {
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        coupon.setEndsAt(LocalDateTime.now().minusDays(1));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate("SAVE10", restaurant(5L), BigDecimal.valueOf(100)))
                .isInstanceOf(CouponNotApplicableException.class);
    }

    @Test
    void validate_throwsCouponNotApplicable_whenScopedToDifferentRestaurant() {
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        coupon.setRestaurant(restaurant(99L));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate("SAVE10", restaurant(5L), BigDecimal.valueOf(100)))
                .isInstanceOf(CouponNotApplicableException.class);
    }

    @Test
    void validate_succeeds_whenScopedToSameRestaurant() {
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        coupon.setRestaurant(restaurant(5L));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        CouponService.CouponApplication application = couponService.validate("SAVE10", restaurant(5L), BigDecimal.valueOf(100));

        assertThat(application.discount()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void validate_throwsCouponNotApplicable_whenBelowMinOrderAmount() {
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        coupon.setMinOrderAmount(BigDecimal.valueOf(200));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate("SAVE10", restaurant(5L), BigDecimal.valueOf(100)))
                .isInstanceOf(CouponNotApplicableException.class);
    }

    @Test
    void validate_throwsCouponNotApplicable_whenUsageLimitAlreadyReached() {
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        coupon.setUsageLimit(5);
        coupon.setId(9L);
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));
        when(couponUsageRepository.countByCouponId(9L)).thenReturn(5L);

        assertThatThrownBy(() -> couponService.validate("SAVE10", restaurant(5L), BigDecimal.valueOf(100)))
                .isInstanceOf(CouponNotApplicableException.class);
    }

    @Test
    void validate_computesPercentageDiscount_cappedAtMaxDiscountAmount() {
        Coupon coupon = activeCoupon("PCT20", DiscountType.PERCENTAGE, BigDecimal.valueOf(20));
        coupon.setMaxDiscountAmount(BigDecimal.valueOf(15));
        when(couponRepository.findByCode("PCT20")).thenReturn(Optional.of(coupon));

        CouponService.CouponApplication application = couponService.validate("PCT20", restaurant(5L), BigDecimal.valueOf(100));

        assertThat(application.discount()).isEqualByComparingTo(BigDecimal.valueOf(15));
    }

    @Test
    void validate_computesPercentageDiscount_withoutCap() {
        Coupon coupon = activeCoupon("PCT20", DiscountType.PERCENTAGE, BigDecimal.valueOf(20));
        when(couponRepository.findByCode("PCT20")).thenReturn(Optional.of(coupon));

        CouponService.CouponApplication application = couponService.validate("PCT20", restaurant(5L), BigDecimal.valueOf(100));

        assertThat(application.discount()).isEqualByComparingTo(BigDecimal.valueOf(20).setScale(2));
    }

    @Test
    void validate_capsFixedDiscount_atSubtotal_soDiscountNeverExceedsIt() {
        Coupon coupon = activeCoupon("BIG", DiscountType.FIXED, BigDecimal.valueOf(500));
        when(couponRepository.findByCode("BIG")).thenReturn(Optional.of(coupon));

        CouponService.CouponApplication application = couponService.validate("BIG", restaurant(5L), BigDecimal.valueOf(100));

        assertThat(application.discount()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void validateForCurrentCart_throwsCartEmpty_whenCartHasNoRow() {
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.validateForCurrentCart("SAVE10"))
                .isInstanceOf(CartEmptyException.class);
    }

    @Test
    void validateForCurrentCart_throwsCartEmpty_whenCartHasNoItems() {
        Cart cart = new Cart();
        cart.setRestaurant(restaurant(5L));
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> couponService.validateForCurrentCart("SAVE10"))
                .isInstanceOf(CartEmptyException.class);
    }

    @Test
    void validateForCurrentCart_throwsRestaurantNotFound_whenRestaurantNoLongerVisible() {
        Restaurant restaurant = restaurant(5L);
        restaurant.setApprovalStatus(RestaurantApprovalStatus.SUSPENDED);
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> couponService.validateForCurrentCart("SAVE10"))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void validateForCurrentCart_returnsPreview_derivedFromCallersCart() {
        Restaurant restaurant = restaurant(5L);
        restaurant.setDeliveryFee(BigDecimal.valueOf(12));
        Cart cart = cartWithOneItem(restaurant);
        when(cartRepository.findByCustomerIdWithItems(1L)).thenReturn(Optional.of(cart));
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        CouponService.CouponApplicationPreview preview = couponService.validateForCurrentCart("SAVE10");

        assertThat(preview.subtotal()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(preview.deliveryFee()).isEqualByComparingTo(BigDecimal.valueOf(12));
        assertThat(preview.application().discount()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void recordUsage_savesUsage_whenUnderLimit() {
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        coupon.setId(9L);
        coupon.setUsageLimit(5);
        when(couponRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(coupon));
        when(couponUsageRepository.countByCouponId(9L)).thenReturn(3L);
        Order order = new Order();
        order.setCustomer(new User());

        couponService.recordUsage(coupon, order);

        ArgumentCaptor<com.food.foodapp.coupon.entity.CouponUsage> captor =
                ArgumentCaptor.forClass(com.food.foodapp.coupon.entity.CouponUsage.class);
        verify(couponUsageRepository).save(captor.capture());
        assertThat(captor.getValue().getCoupon()).isEqualTo(coupon);
        assertThat(captor.getValue().getOrder()).isEqualTo(order);
    }

    @Test
    void recordUsage_throwsCouponNotApplicable_whenLimitReachedUnderLock() {
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        coupon.setId(9L);
        coupon.setUsageLimit(5);
        when(couponRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(coupon));
        when(couponUsageRepository.countByCouponId(9L)).thenReturn(5L);
        Order order = new Order();
        order.setCustomer(new User());

        assertThatThrownBy(() -> couponService.recordUsage(coupon, order))
                .isInstanceOf(CouponNotApplicableException.class);
        verify(couponUsageRepository, never()).save(any());
    }

    @Test
    void recordUsage_throwsCouponNotFound_whenCouponRowNoLongerExists() {
        Coupon coupon = activeCoupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        coupon.setId(9L);
        when(couponRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.recordUsage(coupon, new Order()))
                .isInstanceOf(CouponNotFoundException.class);
    }

    private Coupon activeCoupon(String code, DiscountType type, BigDecimal value) {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setCode(code);
        coupon.setDiscountType(type);
        coupon.setDiscountValue(value);
        coupon.setActive(true);
        return coupon;
    }

    private Restaurant restaurant(Long id) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(id);
        restaurant.setName("Pizza Place");
        restaurant.setDeliveryFee(BigDecimal.valueOf(12));
        restaurant.setApprovalStatus(RestaurantApprovalStatus.APPROVED);
        restaurant.setOpenForOrders(true);
        return restaurant;
    }

    private Cart cartWithOneItem(Restaurant restaurant) {
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setRestaurant(restaurant);
        CartItem item = new CartItem();
        item.setId(1L);
        MenuItem menuItem = new MenuItem();
        menuItem.setId(10L);
        menuItem.setName("Pizza");
        menuItem.setPrice(BigDecimal.valueOf(50));
        menuItem.setAvailable(true);
        item.setMenuItem(menuItem);
        item.setQuantity(2);
        cart.setItems(java.util.List.of(item));
        return cart;
    }
}
