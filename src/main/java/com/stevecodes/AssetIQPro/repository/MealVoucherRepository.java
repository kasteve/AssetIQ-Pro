package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.MealVoucher;
import com.stevecodes.AssetIQPro.entity.MealVoucher.MealType;
import com.stevecodes.AssetIQPro.entity.MealVoucher.VoucherStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MealVoucherRepository extends JpaRepository<MealVoucher, Long> {

    Optional<MealVoucher> findByVoucherCode(String voucherCode);

    List<MealVoucher> findByUserIdOrderByGeneratedAtDesc(Long userId);

    List<MealVoucher> findByVoucherDateOrderByGeneratedAtDesc(LocalDate date);

    @Query("SELECT v FROM MealVoucher v WHERE v.userId = :userId AND v.mealType = :mealType AND v.voucherDate = :voucherDate")
    Optional<MealVoucher> findByUserAndMealTypeAndDate(@Param("userId") Long userId,
                                                       @Param("mealType") MealType mealType,
                                                       @Param("voucherDate") LocalDate voucherDate);

    @Query("SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END FROM MealVoucher v " +
            "WHERE v.userId = :userId AND v.mealType = :mealType AND v.voucherDate = :voucherDate")
    boolean existsByUserAndMealTypeAndDate(@Param("userId") Long userId,
                                           @Param("mealType") MealType mealType,
                                           @Param("voucherDate") LocalDate voucherDate);

    List<MealVoucher> findByStatusOrderByGeneratedAtDesc(VoucherStatus status);

    @Query("SELECT COUNT(v) FROM MealVoucher v WHERE v.voucherDate = :date AND v.mealType = :mealType")
    long countByVoucherDateAndMealType(@Param("date") LocalDate date,
                                       @Param("mealType") MealType mealType);

    @Query("SELECT v FROM MealVoucher v WHERE v.voucherDate BETWEEN :startDate AND :endDate ORDER BY v.generatedAt DESC")
    List<MealVoucher> findByDateRange(@Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    Object countByVoucherDate(LocalDate date);

    Object countByVoucherDateAndStatus(LocalDate date, VoucherStatus voucherStatus);
}
