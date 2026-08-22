package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Notification;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationAsyncService {

    private final NotificationRepository notificationRepository;

    @Async("notificationTaskExecutor")
    public void createNotificationAsync(Long userId, String message, String type) {
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setMessage(message);
            notification.setType(type);
            notification.setRead(false);
            notificationRepository.save(notification);
            log.info("✅ Notification created asynchronously for user: {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to create notification asynchronously: {}", e.getMessage());
        }
    }

    @Async("notificationTaskExecutor")
    public void createNotificationAsync(String userEmail, String message, String type) {
        // Find user by email and create notification
        // Similar to above
    }

    @Async("notificationTaskExecutor")
    public void sendBulkNotificationsAsync(Long[] userIds, String message, String type) {
        try {
            for (Long userId : userIds) {
                createNotificationAsync(userId, message, type);
            }
            log.info("✅ Bulk notifications sent to {} users", userIds.length);
        } catch (Exception e) {
            log.error("❌ Failed to send bulk notifications: {}", e.getMessage());
        }
    }
}