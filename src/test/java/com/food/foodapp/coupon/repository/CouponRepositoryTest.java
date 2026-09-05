package com.food.foodapp.coupon.repository;

import com.food.foodapp.coupon.entity.Coupon;
import com.food.foodapp.coupon.entity.DiscountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.food.foodapp.support.RepositoryTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence-level checks for the {@code @Check} constraints on {@link Coupon} — invariants
 * that only bite at flush time against the real schema, plus the custom lookup queries.
 */
@RepositoryTest
class CouponRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    void findByCode_returnsCoupon_whenExists() {
        entityManager.persist(coupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN));
        entityManager.flush();
        entityManager.clear();

        Optional<Coupon> found = couponRepository.findByCode("SAVE10");

        assertThat(found).isPresent();
        assertThat(found.get().getDiscountType()).isEqualTo(DiscountType.FIXED);
    }

    @Test
    void findByCode_isEmpty_whenNoCouponMatches() {
        Optional<Coupon> found = couponRepository.findByCode("MISSING");

        assertThat(found).isEmpty();
    }

    @Test
    void findByIdForUpdate_locksAndReturnsCoupon() {
        Coupon coupon = coupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN);
        entityManager.persist(coupon);
        entityManager.flush();
        entityManager.clear();

        Optional<Coupon> found = couponRepository.findByIdForUpdate(coupon.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("SAVE10");
    }

    @Test
    void savingSecondCoupon_withSameCode_violatesUniqueConstraint() {
        entityManager.persist(coupon("SAVE10", DiscountType.FIXED, BigDecimal.TEN));
        entityManager.flush();

        Coupon duplicate = coupon("SAVE10", DiscountType.FIXED, BigDecimal.valueOf(5));

        assertThatThrownBy(() -> couponRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savingCoupon_withZeroDiscountValue_violatesCheckConstraint() {
        Coupon coupon = coupon("ZERO", DiscountType.FIXED, BigDecimal.ZERO);

        assertThatThrownBy(() -> couponRepository.saveAndFlush(coupon))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savingCoupon_withPercentageOver100_violatesCheckConstraint() {
        Coupon coupon = coupon("OVER100", DiscountType.PERCENTAGE, BigDecimal.valueOf(150));

        assertThatThrownBy(() -> couponRepository.saveAndFlush(coupon))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savingCoupon_withEndsAtBeforeStartsAt_violatesCheckConstraint() {
        Coupon coupon = coupon("BADWINDOW", DiscountType.FIXED, BigDecimal.TEN);
        coupon.setStartsAt(LocalDateTime.now());
        coupon.setEndsAt(LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> couponRepository.saveAndFlush(coupon))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Coupon coupon(String code, DiscountType type, BigDecimal value) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(type);
        coupon.setDiscountValue(value);
        coupon.setActive(true);
        return coupon;
    }
}
