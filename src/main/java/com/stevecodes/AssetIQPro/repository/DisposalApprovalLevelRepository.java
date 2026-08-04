package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.DisposalApprovalLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisposalApprovalLevelRepository extends JpaRepository<DisposalApprovalLevel, Integer> {

    // ============================================
    // BASIC QUERIES
    // ============================================

    /**
     * Find all levels ordered by level_order
     */
    List<DisposalApprovalLevel> findAllByOrderByLevelOrderAsc();

    /**
     * Find all mandatory levels ordered by level_order
     */
    List<DisposalApprovalLevel> findByIsMandatoryTrueOrderByLevelOrderAsc();

    /**
     * Find all active levels
     */
    @Query("SELECT dal FROM DisposalApprovalLevel dal WHERE dal.isMandatory = true ORDER BY dal.levelOrder")
    List<DisposalApprovalLevel> findActiveLevels();

    /**
     * Find a level by its name
     */
    Optional<DisposalApprovalLevel> findByLevelName(String levelName);

    /**
     * Find a level by its order
     */
    Optional<DisposalApprovalLevel> findByLevelOrder(Integer levelOrder);

    // ============================================
    // PERMISSION-BASED QUERIES
    // ============================================

    /**
     * Find levels that require a specific permission
     */
    List<DisposalApprovalLevel> findByPermissionName(String permissionName);

    /**
     * Find levels that require any of the given permissions
     */
    @Query("SELECT dal FROM DisposalApprovalLevel dal WHERE dal.permissionName IN :permissionNames")
    List<DisposalApprovalLevel> findByPermissionNames(@Param("permissionNames") List<String> permissionNames);

    /**
     * Find levels where a specific role can approve
     */
    List<DisposalApprovalLevel> findByRoleName(String roleName);

    // ============================================
    // SLA-BASED QUERIES
    // ============================================

    /**
     * Get total SLA hours for all mandatory levels
     */
    @Query("SELECT SUM(dal.slaHours) FROM DisposalApprovalLevel dal WHERE dal.isMandatory = true")
    Integer getTotalSLAMandatoryHours();

    /**
     * Get levels with SLA hours greater than a threshold
     */
    List<DisposalApprovalLevel> findBySlaHoursGreaterThan(Integer slaHours);

    /**
     * Get levels that have escalation configured
     */
    @Query("SELECT dal FROM DisposalApprovalLevel dal WHERE dal.escalationAfterHours IS NOT NULL " +
            "AND dal.escalationAfterHours > 0")
    List<DisposalApprovalLevel> findLevelsWithEscalation();

    // ============================================
    // CHECK METHODS
    // ============================================

    /**
     * Check if a level name exists
     */
    boolean existsByLevelName(String levelName);

    /**
     * Check if a level order exists
     */
    boolean existsByLevelOrder(Integer levelOrder);

    /**
     * Get the next level order after a given order
     */
    @Query("SELECT MIN(dal.levelOrder) FROM DisposalApprovalLevel dal " +
            "WHERE dal.levelOrder > :order AND dal.isMandatory = true")
    Integer findNextLevelOrder(@Param("order") Integer order);

    /**
     * Get the previous level order before a given order
     */
    @Query("SELECT MAX(dal.levelOrder) FROM DisposalApprovalLevel dal " +
            "WHERE dal.levelOrder < :order AND dal.isMandatory = true")
    Integer findPreviousLevelOrder(@Param("order") Integer order);

    // ============================================
    // AGGREGATION QUERIES
    // ============================================

    /**
     * Count levels by role
     */
    @Query("SELECT dal.roleName, COUNT(dal) FROM DisposalApprovalLevel dal " +
            "GROUP BY dal.roleName")
    List<Object[]> countByRole();

    /**
     * Get all level names as a list
     */
    @Query("SELECT dal.levelName FROM DisposalApprovalLevel dal ORDER BY dal.levelOrder")
    List<String> findAllLevelNames();

    /**
     * Get level order by name
     */
    @Query("SELECT dal.levelOrder FROM DisposalApprovalLevel dal WHERE dal.levelName = :levelName")
    Optional<Integer> findLevelOrderByName(@Param("levelName") String levelName);
}