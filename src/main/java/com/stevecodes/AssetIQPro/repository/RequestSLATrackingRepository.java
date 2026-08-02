package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.RequestSLATracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RequestSLATrackingRepository extends JpaRepository<RequestSLATracking, Long> {

    Optional<RequestSLATracking> findByRequestIdAndRequestType(Long requestId, String requestType);

    List<RequestSLATracking> findByStatus(String status);

    long countByStatus(String status);

    @Query("SELECT rst FROM RequestSLATracking rst WHERE rst.status = 'IN_PROGRESS' AND rst.slaDueAt <= :date")
    List<RequestSLATracking> findBreachedTrackings(@Param("date") LocalDateTime date);

    // ✅ FIXED: Correct method signature for the service call
    @Query("SELECT rst FROM RequestSLATracking rst WHERE rst.status = :status AND rst.slaDueAt BETWEEN :startDate AND :endDate")
    List<RequestSLATracking> findByStatusAndSlaDueAtBetween(@Param("status") String status,
                                                            @Param("startDate") LocalDateTime startDate,
                                                            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT rst FROM RequestSLATracking rst WHERE rst.status = 'IN_PROGRESS' AND (rst.lastReminderSentAt IS NULL OR rst.lastReminderSentAt <= :reminderDate)")
    List<RequestSLATracking> findTrackingsForReminder(@Param("reminderDate") LocalDateTime reminderDate);

    List<RequestSLATracking> findByRequestType(String requestType);

    @Query("SELECT COUNT(rst) FROM RequestSLATracking rst WHERE rst.status = 'IN_PROGRESS' AND rst.requestType = :requestType")
    long countActiveByRequestType(@Param("requestType") String requestType);

    @Query("SELECT rst FROM RequestSLATracking rst WHERE rst.status = 'IN_PROGRESS' AND rst.escalatedTo IS NOT NULL")
    List<RequestSLATracking> findEscalatedTrackings();

    @Query("SELECT rst FROM RequestSLATracking rst WHERE rst.status = 'IN_PROGRESS' AND rst.escalatedTo = :userId")
    List<RequestSLATracking> findEscalatedToUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(rst) FROM RequestSLATracking rst WHERE rst.status = 'COMPLETED' AND rst.requestType = :requestType")
    long countCompletedByRequestType(@Param("requestType") String requestType);

    @Query("SELECT COUNT(rst) FROM RequestSLATracking rst WHERE rst.status = 'BREACHED' AND rst.requestType = :requestType")
    long countBreachedByRequestType(@Param("requestType") String requestType);

    long count();

    @Query("SELECT rst FROM RequestSLATracking rst WHERE rst.status = 'IN_PROGRESS' AND rst.slaDueAt > :now ORDER BY rst.slaDueAt ASC")
    List<RequestSLATracking> findActiveTrackingsOrderByDueDate(@Param("now") LocalDateTime now);

    @Query("SELECT rst FROM RequestSLATracking rst WHERE rst.status = 'IN_PROGRESS' AND rst.slaDueAt < :threshold AND rst.breachesCount < :maxBreaches")
    List<RequestSLATracking> findTrackingsNearBreach(@Param("threshold") LocalDateTime threshold,
                                                     @Param("maxBreaches") Integer maxBreaches);

    @Query("SELECT rst FROM RequestSLATracking rst WHERE rst.slaConfigId = :configId")
    List<RequestSLATracking> findBySlaConfigId(@Param("configId") Integer configId);
}