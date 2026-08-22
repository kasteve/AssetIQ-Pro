package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Notification;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncNotificationService {

    private final NotificationRepository notificationRepository;
    private final BaseUrlService baseUrlService;

    @Async("notificationTaskExecutor")
    public void createNotificationAsync(Long userId, String type, String title, String message, String link) {
        try {
            log.info("📨 Creating async notification for user: {} - Type: {}", userId, type);
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setLink(link != null ? baseUrlService.buildUrl(link) : null);
            notification.setRead(false);
            notification.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
            log.info("✅ Notification created for user: {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to create notification for user {}: {}", userId, e.getMessage());
        }
    }

    @Async("notificationTaskExecutor")
    public void createBulkNotificationsAsync(List<Long> userIds, String type, String title, String message, String link) {
        try {
            log.info("📨 Creating bulk notifications for {} users", userIds.size());
            for (Long userId : userIds) {
                createNotificationAsync(userId, type, title, message, link);
            }
            log.info("✅ Bulk notifications sent to {} users", userIds.size());
        } catch (Exception e) {
            log.error("❌ Failed to send bulk notifications: {}", e.getMessage());
        }
    }
}