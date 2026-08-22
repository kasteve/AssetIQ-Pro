package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.EmailQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailQueueRepository extends JpaRepository<EmailQueue, Long> {

    List<EmailQueue> findByStatusAndNextRetryAtBefore(String status, LocalDateTime dateTime);

    List<EmailQueue> findByStatusOrderByCreatedAtAsc(String status);

    List<EmailQueue> findByStatus(String status);

    long countByStatus(String status);

    @Query("SELECT COUNT(e) FROM EmailQueue e WHERE e.status = 'FAILED'")
    long countFailedEmails();

    @Query("SELECT COUNT(e) FROM EmailQueue e WHERE e.status = 'SENT' AND e.sentAt >= :since")
    long countSentSince(@Param("since") LocalDateTime since);

    // ✅ FIX: Use recipientEmail instead of to
    List<EmailQueue> findByToOrderByCreatedAtDesc(String to);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmailQueue e WHERE e.status = 'SENT' AND e.sentAt < :cutoffDate")
    int deleteOldSentEmails(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Transactional
    @Query("UPDATE EmailQueue e SET e.status = 'PENDING', e.retryCount = 0, e.nextRetryAt = :nextRetryTime WHERE e.status = 'FAILED'")
    void resetFailedEmails(@Param("nextRetryTime") LocalDateTime nextRetryTime);

    @Query("SELECT e FROM EmailQueue e WHERE e.status = 'FAILED' ORDER BY e.createdAt DESC")
    List<EmailQueue> findFailedEmails();

    @Query("SELECT e FROM EmailQueue e WHERE e.status = 'PENDING' AND e.retryCount < e.maxRetries ORDER BY e.nextRetryAt ASC")
    List<EmailQueue> findPendingEmailsWithRetriesLeft();

    @Query("SELECT e FROM EmailQueue e WHERE e.status = 'PENDING' AND e.createdAt < :stuckThreshold")
    List<EmailQueue> findStuckPendingEmails(@Param("stuckThreshold") LocalDateTime stuckThreshold);

    @Modifying
    @Transactional
    @Query("UPDATE EmailQueue e SET e.status = 'FAILED', e.errorMessage = :errorMessage WHERE e.id = :id")
    void markAsFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    @Query("SELECT e.status, COUNT(e) FROM EmailQueue e GROUP BY e.status")
    List<Object[]> countByStatusGroup();

    @Query("SELECT e FROM EmailQueue e WHERE e.status = 'PENDING' AND e.nextRetryAt <= :now ORDER BY e.nextRetryAt ASC")
    List<EmailQueue> findEmailsReadyForRetry(@Param("now") LocalDateTime now);
}