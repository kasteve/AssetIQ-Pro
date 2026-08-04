package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.DisposalApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface DisposalApprovalRepository extends JpaRepository<DisposalApproval, Long> {

    // ============================================
    // BASIC QUERIES
    // ============================================

    /**
     * Find all approvals for a specific disposal request, ordered by level order
     */
    List<DisposalApproval> findByDisposalRequestIdOrderByLevelOrderAsc(Long disposalRequestId);

    /**
     * Find a specific approval by disposal request ID and level order
     */
    Optional<DisposalApproval> findByDisposalRequestIdAndLevelOrder(Long disposalRequestId, Integer levelOrder);

    /**
     * Find all pending approvals for a specific user
     */
    List<DisposalApproval> findByApproverIdAndStatus(Long approverId, String status);

    /**
     * Find all approvals with a specific status
     */
    List<DisposalApproval> findByStatus(String status);

    /**
     * Find all overdue pending approvals
     */
    List<DisposalApproval> findByStatusAndDueDateBefore(String status, LocalDateTime dueDate);

    /**
     * Find all approvals for a specific disposal request
     */
    List<DisposalApproval> findByDisposalRequestId(Long disposalRequestId);

    // ============================================
    // STATUS-BASED QUERIES
    // ============================================

    /**
     * Count pending approvals for a specific user
     */
    long countByApproverIdAndStatus(Long approverId, String status);

    /**
     * Count approvals by status for a specific request
     */
    @Query("SELECT COUNT(da) FROM DisposalApproval da WHERE da.disposalRequestId = :requestId AND da.status = :status")
    long countByDisposalRequestIdAndStatus(@Param("requestId") Long requestId, @Param("status") String status);

    /**
     * Check if all approvals for a request are approved
     */
    @Query("SELECT COUNT(da) FROM DisposalApproval da WHERE da.disposalRequestId = :requestId AND da.status != 'APPROVED'")
    long countNonApprovedByDisposalRequestId(@Param("requestId") Long requestId);

    /**
     * Check if any approval for a request is rejected
     */
    @Query("SELECT COUNT(da) > 0 FROM DisposalApproval da WHERE da.disposalRequestId = :requestId AND da.status = 'REJECTED'")
    boolean existsRejectedByDisposalRequestId(@Param("requestId") Long requestId);

    // ============================================
    // ESCALATION QUERIES
    // ============================================

    /**
     * Find all pending approvals that need escalation (overdue + escalation_count < max)
     */
    @Query("SELECT da FROM DisposalApproval da WHERE da.status = 'PENDING' " +
            "AND da.dueDate < :now AND da.escalationCount < :maxEscalations")
    List<DisposalApproval> findOverduePendingApprovals(@Param("now") LocalDateTime now,
                                                       @Param("maxEscalations") int maxEscalations);

    /**
     * Find approvals that have been escalated but not yet acted upon
     */
    @Query("SELECT da FROM DisposalApproval da WHERE da.status = 'PENDING' " +
            "AND da.escalatedAt IS NOT NULL AND da.escalationCount > 0")
    List<DisposalApproval> findEscalatedPendingApprovals();

    // ============================================
    // USER-BASED QUERIES
    // ============================================

    /**
     * Find all approvals for a user (both pending and approved)
     */
    @Query("SELECT da FROM DisposalApproval da WHERE da.approverId = :userId ORDER BY da.createdAt DESC")
    List<DisposalApproval> findByApproverId(@Param("userId") Long userId);

    /**
     * Find pending approvals for a user with optional filtering by request ID
     */
    @Query("SELECT da FROM DisposalApproval da WHERE da.approverId = :userId " +
            "AND da.status = 'PENDING' AND (:requestId IS NULL OR da.disposalRequestId = :requestId)")
    List<DisposalApproval> findPendingByApproverIdAndRequestId(@Param("userId") Long userId,
                                                               @Param("requestId") Long requestId);

    /**
     * Get all approvals where a specific user is the approver, with pagination
     */
    @Query("SELECT da FROM DisposalApproval da WHERE da.approverId = :userId ORDER BY da.createdAt DESC")
    List<DisposalApproval> findByApproverIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    // ============================================
    // RECENT ACTIVITY QUERIES
    // ============================================

    /**
     * Find recently approved/rejected approvals
     */
    @Query("SELECT da FROM DisposalApproval da WHERE da.status IN ('APPROVED', 'REJECTED') " +
            "AND (da.approvedAt IS NOT NULL OR da.rejectedAt IS NOT NULL) " +
            "ORDER BY COALESCE(da.approvedAt, da.rejectedAt) DESC")
    List<DisposalApproval> findRecentApprovals();

    /**
     * Get approvals that are close to their due date (within 24 hours)
     */
    @Query("SELECT da FROM DisposalApproval da WHERE da.status = 'PENDING' " +
            "AND da.dueDate BETWEEN :now AND :threshold")
    List<DisposalApproval> findApprovalsDueWithin(@Param("now") LocalDateTime now,
                                                  @Param("threshold") LocalDateTime threshold);

    // ============================================
    // AGGREGATION QUERIES
    // ============================================

    /**
     * Get approval statistics for a request
     */
    @Query("SELECT new map(da.status as status, COUNT(da) as count) " +
            "FROM DisposalApproval da WHERE da.disposalRequestId = :requestId " +
            "GROUP BY da.status")
    List<Map<String, Object>> getApprovalStatistics(@Param("requestId") Long requestId);

    /**
     * Get average approval time for completed approvals
     */
    @Query("SELECT AVG(TIMESTAMPDIFF(HOUR, da.createdAt, da.approvedAt)) " +
            "FROM DisposalApproval da WHERE da.status = 'APPROVED'")
    Double getAverageApprovalTime();

    /**
     * Get approval time by level
     */
    @Query("SELECT da.levelOrder, AVG(TIMESTAMPDIFF(HOUR, da.createdAt, da.approvedAt)) " +
            "FROM DisposalApproval da WHERE da.status = 'APPROVED' " +
            "GROUP BY da.levelOrder ORDER BY da.levelOrder")
    List<Object[]> getAverageApprovalTimeByLevel();

    // ============================================
    // BULK OPERATIONS
    // ============================================

    /**
     * Delete all approvals for a disposal request (used when request is cancelled/deleted)
     */
    void deleteByDisposalRequestId(Long disposalRequestId);

    /**
     * Update status for all approvals of a request
     */
    @Query("UPDATE DisposalApproval da SET da.status = :status WHERE da.disposalRequestId = :requestId")
    void updateStatusByDisposalRequestId(@Param("requestId") Long requestId,
                                         @Param("status") String status);

    /**
     * Mark all pending approvals as escalated for a request
     */
    @Query("UPDATE DisposalApproval da SET da.escalatedAt = :now " +
            "WHERE da.disposalRequestId = :requestId AND da.status = 'PENDING'")
    void escalateAllPendingByDisposalRequestId(@Param("requestId") Long requestId,
                                               @Param("now") LocalDateTime now);
}