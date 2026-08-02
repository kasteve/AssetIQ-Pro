package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.SLANotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SLANotificationRepository extends JpaRepository<SLANotification, Long> {

    List<SLANotification> findByRecipientIdOrderBySentAtDesc(Long recipientId);

    List<SLANotification> findByRecipientIdAndReadAtIsNullOrderBySentAtDesc(Long recipientId);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    List<SLANotification> findByNotificationTypeAndSentAtBefore(String notificationType, LocalDateTime sentAt);

    List<SLANotification> findByRecipientIdAndReadAtIsNull(Long userId);

    @Query("SELECT sn FROM SLANotification sn WHERE sn.recipientId = :userId AND sn.readAt IS NULL ORDER BY sn.sentAt DESC")
    List<SLANotification> findUnreadByRecipient(@Param("userId") Long userId);

    @Query("SELECT COUNT(sn) FROM SLANotification sn WHERE sn.recipientId = :userId AND sn.readAt IS NULL")
    long countUnreadByRecipient(@Param("userId") Long userId);

    @Query("SELECT sn FROM SLANotification sn WHERE sn.requestId = :requestId AND sn.requestType = :requestType ORDER BY sn.sentAt DESC")
    List<SLANotification> findByRequestIdAndRequestType(@Param("requestId") Long requestId,
                                                        @Param("requestType") String requestType);

    @Query("SELECT sn FROM SLANotification sn WHERE sn.sentAt < :date AND sn.readAt IS NULL")
    List<SLANotification> findUnreadOlderThan(@Param("date") LocalDateTime date);

    @Query("SELECT sn FROM SLANotification sn WHERE sn.notificationType = :type AND sn.readAt IS NULL")
    List<SLANotification> findUnreadByType(@Param("type") String type);

    void deleteByRecipientIdAndReadAtIsNotNull(Long recipientId);
}