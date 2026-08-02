package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.SLAEscalationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SLAEscalationHistoryRepository extends JpaRepository<SLAEscalationHistory, Long> {

    List<SLAEscalationHistory> findByRequestIdAndRequestTypeOrderByEscalatedAtDesc(Long requestId, String requestType);

    List<SLAEscalationHistory> findByEscalatedTo(Long userId);

    List<SLAEscalationHistory> findByNotificationSentFalse();

    @Query("SELECT seh FROM SLAEscalationHistory seh WHERE seh.requestId = :requestId ORDER BY seh.escalatedAt DESC")
    List<SLAEscalationHistory> findByRequestIdOrderByEscalatedAtDesc(@Param("requestId") Long requestId);

    @Query("SELECT seh FROM SLAEscalationHistory seh WHERE seh.escalatedTo = :userId AND seh.actionTaken = false")
    List<SLAEscalationHistory> findPendingActionsForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(seh) FROM SLAEscalationHistory seh WHERE seh.requestId = :requestId AND seh.actionTaken = true")
    long countActionsTakenForRequest(@Param("requestId") Long requestId);

    @Query("SELECT seh FROM SLAEscalationHistory seh WHERE seh.escalatedBy = :userId ORDER BY seh.escalatedAt DESC")
    List<SLAEscalationHistory> findByEscalatedBy(@Param("userId") Long userId);

    @Query("SELECT seh FROM SLAEscalationHistory seh WHERE seh.requestType = :requestType ORDER BY seh.escalatedAt DESC")
    List<SLAEscalationHistory> findByRequestType(@Param("requestType") String requestType);
}