package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.AssetRetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRetentionPolicyRepository extends JpaRepository<AssetRetentionPolicy, Integer> {

    // ============================================
    // BASIC QUERIES
    // ============================================

    /**
     * Find all active retention policies
     */
    List<AssetRetentionPolicy> findByIsActiveTrue();

    /**
     * Find retention policy by category name
     */
    Optional<AssetRetentionPolicy> findByCategoryName(String categoryName);

    /**
     * Find retention policies by disposal method
     */
    List<AssetRetentionPolicy> findByDisposalMethod(String disposalMethod);

    // ============================================
    // COMPLEX QUERIES
    // ============================================

    /**
     * Find policies where retention period is greater than a threshold
     */
    List<AssetRetentionPolicy> findByRetentionPeriodYearsGreaterThan(Integer years);

    /**
     * Find policies where retention period is less than a threshold
     */
    List<AssetRetentionPolicy> findByRetentionPeriodYearsLessThan(Integer years);

    /**
     * Get default retention policy (if no category-specific one exists)
     */
    @Query("SELECT arp FROM AssetRetentionPolicy arp WHERE arp.isActive = true " +
            "AND arp.categoryName = 'DEFAULT'")
    Optional<AssetRetentionPolicy> findDefaultPolicy();

    // ============================================
    // CHECK METHODS
    // ============================================

    /**
     * Check if a policy exists for a category
     */
    boolean existsByCategoryName(String categoryName);

    /**
     * Get all active category names
     */
    @Query("SELECT arp.categoryName FROM AssetRetentionPolicy arp WHERE arp.isActive = true")
    List<String> findAllActiveCategoryNames();
}