package com.food.foodapp.coupon.repository;

import com.food.foodapp.coupon.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /** {@code code} is always looked up upper-cased — see {@code CouponService#normalizeCode}. */
    Optional<Coupon> findByCode(String code);

    /**
     * Row-locks the coupon for the rest of the caller's transaction. Used only by
     * {@code CouponService#recordUsage}, right before it re-counts existing {@code CouponUsage}
     * rows and inserts a new one, so two concurrent checkouts redeeming the last remaining use of
     * a limited coupon serialize instead of both passing the limit check and oversubscribing it —
     * the same race the pessimistic lock on {@code Cart} already guards against for cart mutations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);
}
