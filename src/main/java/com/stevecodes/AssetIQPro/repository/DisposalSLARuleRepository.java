package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.DisposalSLARule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisposalSLARuleRepository extends JpaRepository<DisposalSLARule, Integer> {

    // ============================================
    // BASIC QUERIES
    // ============================================

    /**
     * Find all active SLA rules
     */
    List<DisposalSLARule> findByIsActiveTrue();

    /**
     * Find SLA rule by name
     */
    Optional<DisposalSLARule> findByRuleName(String ruleName);

    /**
     * Find SLA rules for a specific asset category
     */
    List<DisposalSLARule> findByAssetCategoryId(Integer assetCategoryId);

    /**
     * Find SLA rules for a specific asset status
     */
    List<DisposalSLARule> findByAssetStatus(String assetStatus);

    // ============================================
    // COMPLEX QUERIES
    // ============================================

    /**
     * Find the most appropriate SLA rule for an asset
     * Prefers category-specific rules, then general rules
     */
    @Query("SELECT dsr FROM DisposalSLARule dsr WHERE dsr.isActive = true " +
            "AND (:categoryId IS NULL OR dsr.assetCategoryId = :categoryId) " +
            "AND (:status IS NULL OR dsr.assetStatus = :status) " +
            "ORDER BY CASE WHEN dsr.assetCategoryId IS NOT NULL THEN 0 ELSE 1 END, dsr.slaRuleId")
    List<DisposalSLARule> findApplicableRules(@Param("categoryId") Integer categoryId,
                                              @Param("status") String status);

    /**
     * Get the default SLA rule (no category or status restrictions)
     */
    @Query("SELECT dsr FROM DisposalSLARule dsr WHERE dsr.isActive = true " +
            "AND dsr.assetCategoryId IS NULL AND dsr.assetStatus IS NULL")
    Optional<DisposalSLARule> findDefaultRule();

    /**
     * Find rules with total SLA hours less than a threshold
     */
    List<DisposalSLARule> findByTotalSlaHoursLessThan(Integer slaHours);

    // ============================================
    // CHECK METHODS
    // ============================================

    /**
     * Check if a rule exists for a category
     */
    boolean existsByAssetCategoryId(Integer assetCategoryId);

    /**
     * Check if a rule exists for a status
     */
    boolean existsByAssetStatus(String assetStatus);
}