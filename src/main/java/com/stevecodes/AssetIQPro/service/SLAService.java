package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.*;
import com.stevecodes.AssetIQPro.repository.RequestSLATrackingRepository;
import com.stevecodes.AssetIQPro.repository.SLAConfigurationRepository;
import com.stevecodes.AssetIQPro.repository.SLAEscalationHistoryRepository;
import com.stevecodes.AssetIQPro.repository.SLANotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SLAService {

    private final RequestSLATrackingRepository trackingRepository;
    private final SLAConfigurationRepository slaConfigRepository;
    private final SLAEscalationHistoryRepository escalationHistoryRepository;
    private final SLANotificationRepository slaNotificationRepository;
    private final AppUserService userService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final BaseUrlService baseUrlService;
    private final AuditService auditService;

    // ============================================
    // SLA TRACKING MANAGEMENT
    // ============================================

    /**
     * Start SLA tracking for a request.
     * IMPORTANT: This method is NOT @Transactional to prevent rollback propagation.
     * The calling service manages its own transaction.
     */
    public RequestSLATracking startSLATracking(Long requestId, String requestType, Long userId) {
        log.info("=== START SLA TRACKING (NO TRANSACTION) ===");
        log.info("📝 Starting SLA tracking for request: {} (Type: {})", requestId, requestType);

        try {
            // Get SLA configuration
            SLAConfiguration slaConfig = getSLAConfigurationForRequest(requestType);
            log.info("✅ Found SLA config: {} ({} hours)", slaConfig.getConfigName(), slaConfig.getSlaHours());

            // Check if tracking already exists
            Optional<RequestSLATracking> existing = trackingRepository
                    .findByRequestIdAndRequestType(requestId, requestType);
            if (existing.isPresent()) {
                log.info("⚠️ SLA tracking already exists for request: {}", requestId);
                return existing.get();
            }

            // Create tracking record
            RequestSLATracking tracking = new RequestSLATracking();
            tracking.setRequestId(requestId);
            tracking.setRequestType(requestType);
            tracking.setSlaConfigId(slaConfig.getConfigId());
            tracking.setSlaStartedAt(LocalDateTime.now());
            tracking.setSlaDueAt(LocalDateTime.now().plusHours(slaConfig.getSlaHours()));
            tracking.setStatus("IN_PROGRESS");
            tracking.setBreachesCount(0);
            tracking.setEscalationCount(0);
            tracking.setCreatedAt(LocalDateTime.now());
            tracking.setUpdatedAt(LocalDateTime.now());

            // Save tracking record
            RequestSLATracking saved = trackingRepository.save(tracking);
            log.info("✅ SLA tracking saved with ID: {}", saved.getTrackingId());

            // Send notifications (non-critical - don't let them fail the operation)
            try {
                sendSLANotification(
                        userId,
                        "SLA_STARTED",
                        "SLA Started",
                        "Your request (ID: " + requestId + ") has been created. SLA: " + slaConfig.getSlaHours() + " hours.",
                        "/" + getRequestTypePath(requestType) + "/" + requestId
                );
                log.info("✅ SLA notification sent to user: {}", userId);
            } catch (Exception e) {
                log.error("❌ Failed to send SLA notification: {}", e.getMessage(), e);
            }

            // Log audit (non-critical - don't let it fail the operation)
            try {
                auditService.logAction("SLA_STARTED",
                        "SLA started for " + requestType + " request #" + requestId + " with SLA of " + slaConfig.getSlaHours() + " hours",
                        userId);
                log.info("✅ SLA audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log SLA audit: {}", e.getMessage(), e);
            }

            log.info("=== START SLA TRACKING END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Failed to start SLA tracking for request {}: {}", requestId, e.getMessage(), e);
            throw e;
        }
    }

    public SLAConfiguration getSLAConfigurationForRequest(String requestType) {
        log.debug("📋 Getting SLA configuration for request type: {}", requestType);
        return slaConfigRepository.findByRequestTypeAndIsDefaultTrue(requestType)
                .orElseGet(() -> {
                    log.warn("⚠️ No default SLA config found for {}, looking for any active config", requestType);
                    return slaConfigRepository.findFirstByRequestTypeAndIsActiveTrue(requestType)
                            .orElseThrow(() -> new RuntimeException("No SLA configuration found for request type: " + requestType));
                });
    }

    public RequestSLATracking getSLAStatus(Long requestId, String requestType) {
        return trackingRepository.findByRequestIdAndRequestType(requestId, requestType)
                .orElse(null);
    }

    /**
     * Complete SLA tracking for a request.
     * IMPORTANT: This method is NOT @Transactional to prevent rollback propagation.
     */
    public void completeSLATracking(Long requestId, String requestType) {
        log.info("=== COMPLETE SLA TRACKING (NO TRANSACTION) ===");
        log.info("📝 Completing SLA tracking for request: {} (Type: {})", requestId, requestType);

        try {
            trackingRepository.findByRequestIdAndRequestType(requestId, requestType)
                    .ifPresentOrElse(
                            tracking -> {
                                tracking.setStatus("COMPLETED");
                                tracking.setCompletedAt(LocalDateTime.now());
                                trackingRepository.save(tracking);
                                log.info("✅ SLA tracking completed for request: {}", requestId);
                            },
                            () -> log.warn("⚠️ No SLA tracking found for request: {} (Type: {})", requestId, requestType)
                    );
        } catch (Exception e) {
            log.error("❌ Failed to complete SLA tracking: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // SLA CHECKING (Scheduled Job)
    // ============================================

    @Scheduled(cron = "0 0/30 * * * *")
    @Transactional
    public void checkAllSLAs() {
        log.info("=== RUNNING SLA CHECK ===");
        log.info("📊 Running SLA check for all request types");

        try {
            List<RequestSLATracking> trackings = trackingRepository.findByStatus("IN_PROGRESS");
            log.info("📊 Found {} active SLA trackings", trackings.size());

            for (RequestSLATracking tracking : trackings) {
                try {
                    processSLACheck(tracking);
                } catch (Exception e) {
                    log.error("❌ Error processing SLA for request {}: {}", tracking.getRequestId(), e.getMessage(), e);
                }
            }

            log.info("✅ SLA check completed");

        } catch (Exception e) {
            log.error("❌ Error running SLA check: {}", e.getMessage(), e);
        }
    }

    private void processSLACheck(RequestSLATracking tracking) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueAt = tracking.getSlaDueAt();
        long totalHours = ChronoUnit.HOURS.between(tracking.getSlaStartedAt(), dueAt);
        long elapsedHours = ChronoUnit.HOURS.between(tracking.getSlaStartedAt(), now);
        double percentageElapsed = totalHours > 0 ? (double) elapsedHours / totalHours * 100 : 0;

        log.debug("📊 Request {}: {}% elapsed, Due: {}", tracking.getRequestId(), percentageElapsed, dueAt);

        if (now.isAfter(dueAt)) {
            log.warn("⚠️ SLA is past due for request: {}", tracking.getRequestId());
            handleSLABreach(tracking);
            return;
        }

        if (percentageElapsed >= 50 && percentageElapsed < 75) {
            sendSLAReminder(tracking, "50");
        } else if (percentageElapsed >= 75 && percentageElapsed < 90) {
            sendSLAReminder(tracking, "75");
        } else if (percentageElapsed >= 90) {
            sendSLAReminder(tracking, "90");
            if (tracking.getEscalationCount() < 3) {
                escalateSLA(tracking);
            }
        }
    }

    // ============================================
    // SLA BREACH HANDLING
    // ============================================

    private void handleSLABreach(RequestSLATracking tracking) {
        log.warn("🚨 SLA BREACHED! Request: {} (Type: {})", tracking.getRequestId(), tracking.getRequestType());

        try {
            tracking.setStatus("BREACHED");
            tracking.setBreachesCount(tracking.getBreachesCount() + 1);
            trackingRepository.save(tracking);
            log.info("✅ SLA marked as BREACHED for request: {}", tracking.getRequestId());

            Long ownerId = getRequestOwner(tracking.getRequestId(), tracking.getRequestType());
            AppUser owner = userService.getUserById(ownerId).orElse(null);
            AppUser manager = getLineManager(ownerId);

            String breachMessage = String.format(
                    "SLA BREACHED! Request #%d (%s) has exceeded its SLA of %d hours.",
                    tracking.getRequestId(),
                    tracking.getRequestType(),
                    getSLAHoursForConfig(tracking.getSlaConfigId())
            );

            // Notify owner
            if (owner != null) {
                try {
                    sendSLANotification(
                            owner.getUserId(),
                            "SLA_BREACHED",
                            "SLA Breached",
                            breachMessage + " Please take immediate action.",
                            "/" + getRequestTypePath(tracking.getRequestType()) + "/" + tracking.getRequestId()
                    );
                    log.info("✅ Owner notified: {}", owner.getUserId());
                } catch (Exception e) {
                    log.error("❌ Failed to notify owner: {}", e.getMessage(), e);
                }
            }

            // Notify manager
            if (manager != null) {
                try {
                    sendSLANotification(
                            manager.getUserId(),
                            "SLA_BREACHED_ESCALATED",
                            "SLA Breached - Employee Alert",
                            "Employee: " + (owner != null ? owner.getFullName() : "Unknown") + "\n" + breachMessage,
                            "/" + getRequestTypePath(tracking.getRequestType()) + "/" + tracking.getRequestId()
                    );
                    log.info("✅ Manager notified: {}", manager.getUserId());
                } catch (Exception e) {
                    log.error("❌ Failed to notify manager: {}", e.getMessage(), e);
                }
            }

            // Create escalation record
            try {
                SLAEscalationHistory escalation = new SLAEscalationHistory();
                escalation.setRequestId(tracking.getRequestId());
                escalation.setRequestType(tracking.getRequestType());
                escalation.setSlaConfigId(tracking.getSlaConfigId());
                escalation.setEscalationLevel(tracking.getEscalationCount() + 1);
                escalation.setEscalatedTo(manager != null ? manager.getUserId() : ownerId);
                escalation.setEscalatedBy(1L);
                escalation.setEscalatedAt(LocalDateTime.now());
                escalation.setNotificationSent(true);
                escalation.setReason("SLA Breached after " + getSLAHoursForConfig(tracking.getSlaConfigId()) + " hours");
                escalationHistoryRepository.save(escalation);
                log.info("✅ Escalation record created for request: {}", tracking.getRequestId());
            } catch (Exception e) {
                log.error("❌ Failed to create escalation record: {}", e.getMessage(), e);
            }

            // Log audit
            try {
                auditService.logAction("SLA_BREACHED",
                        "SLA breached for " + tracking.getRequestType() + " request #" + tracking.getRequestId(),
                        ownerId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("❌ Error handling SLA breach: {}", e.getMessage(), e);
        }
    }

    // ============================================
    // SLA ESCALATION
    // ============================================

    private void escalateSLA(RequestSLATracking tracking) {
        log.info("📈 Escalating SLA for request: {} (Type: {})", tracking.getRequestId(), tracking.getRequestType());

        try {
            Long ownerId = getRequestOwner(tracking.getRequestId(), tracking.getRequestType());
            AppUser manager = getLineManager(ownerId);

            if (manager == null) {
                log.warn("⚠️ No manager found for escalation. Request: {}", tracking.getRequestId());
                return;
            }

            tracking.setEscalatedTo(manager.getUserId());
            tracking.setEscalationCount(tracking.getEscalationCount() + 1);
            trackingRepository.save(tracking);
            log.info("✅ Escalation count updated to: {}", tracking.getEscalationCount());

            // Create escalation record
            SLAEscalationHistory escalation = new SLAEscalationHistory();
            escalation.setRequestId(tracking.getRequestId());
            escalation.setRequestType(tracking.getRequestType());
            escalation.setSlaConfigId(tracking.getSlaConfigId());
            escalation.setEscalationLevel(tracking.getEscalationCount());
            escalation.setEscalatedTo(manager.getUserId());
            escalation.setEscalatedBy(1L);
            escalation.setEscalatedAt(LocalDateTime.now());
            escalation.setNotificationSent(true);
            escalation.setReason("SLA approaching breach. Request pending for " +
                    ChronoUnit.HOURS.between(tracking.getSlaStartedAt(), LocalDateTime.now()) + " hours");
            escalationHistoryRepository.save(escalation);
            log.info("✅ Escalation record created");

            String escalationMessage = String.format(
                    "SLA Escalation: Request #%d (%s) has been pending for %d hours. Please review and take action.",
                    tracking.getRequestId(),
                    tracking.getRequestType(),
                    ChronoUnit.HOURS.between(tracking.getSlaStartedAt(), LocalDateTime.now())
            );

            // Notify manager
            try {
                sendSLANotification(
                        manager.getUserId(),
                        "SLA_ESCALATED",
                        "SLA Escalated - Action Required",
                        escalationMessage,
                        "/" + getRequestTypePath(tracking.getRequestType()) + "/" + tracking.getRequestId()
                );
                log.info("✅ Escalation notification sent to manager: {}", manager.getUserId());
            } catch (Exception e) {
                log.error("❌ Failed to send escalation notification: {}", e.getMessage(), e);
            }

            // Log audit
            try {
                auditService.logAction("SLA_ESCALATED",
                        "SLA escalated for " + tracking.getRequestType() + " request #" + tracking.getRequestId() + " to " + manager.getUsername(),
                        ownerId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("❌ Error escalating SLA: {}", e.getMessage(), e);
        }
    }

    // ============================================
    // SLA REMINDERS
    // ============================================

    private void sendSLAReminder(RequestSLATracking tracking, String percentage) {
        try {
            Long ownerId = getRequestOwner(tracking.getRequestId(), tracking.getRequestType());
            AppUser owner = userService.getUserById(ownerId).orElse(null);

            if (owner == null) {
                log.warn("⚠️ Owner not found for request: {}", tracking.getRequestId());
                return;
            }

            String reminderMessage = String.format(
                    "SLA Reminder: Request #%d (%s) has used %s%% of its SLA time. Due at: %s",
                    tracking.getRequestId(),
                    tracking.getRequestType(),
                    percentage,
                    tracking.getSlaDueAt().toString()
            );

            // Notify owner
            try {
                sendSLANotification(
                        owner.getUserId(),
                        "SLA_REMINDER",
                        "SLA Reminder - " + percentage + "% Elapsed",
                        reminderMessage,
                        "/" + getRequestTypePath(tracking.getRequestType()) + "/" + tracking.getRequestId()
                );
                log.info("✅ Reminder sent to owner: {} at {}%", owner.getUserId(), percentage);
            } catch (Exception e) {
                log.error("❌ Failed to send reminder to owner: {}", e.getMessage(), e);
            }

            // Notify manager at 75% and 90%
            if ("75".equals(percentage) || "90".equals(percentage)) {
                AppUser manager = getLineManager(ownerId);
                if (manager != null) {
                    try {
                        sendSLANotification(
                                manager.getUserId(),
                                "SLA_REMINDER_MANAGER",
                                "SLA Reminder - Employee Request at " + percentage + "%",
                                "Employee: " + owner.getFullName() + "\n" + reminderMessage,
                                "/" + getRequestTypePath(tracking.getRequestType()) + "/" + tracking.getRequestId()
                        );
                        log.info("✅ Reminder sent to manager: {} at {}%", manager.getUserId(), percentage);
                    } catch (Exception e) {
                        log.error("❌ Failed to send reminder to manager: {}", e.getMessage(), e);
                    }
                }
            }

            tracking.setLastReminderSentAt(LocalDateTime.now());
            trackingRepository.save(tracking);

        } catch (Exception e) {
            log.error("❌ Error sending SLA reminder: {}", e.getMessage(), e);
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private Long getRequestOwner(Long requestId, String requestType) {
        // TODO: Implement proper owner lookup for each request type
        log.warn("⚠️ getRequestOwner not fully implemented for request type: {}. Using admin fallback.", requestType);
        return 1L;
    }

    private AppUser getLineManager(Long userId) {
        if (userId == null) return null;

        return userService.getUserById(userId)
                .flatMap(user -> {
                    if (user.getEmployee() != null && user.getEmployee().getLineManager() != null) {
                        Long lineManagerEmployeeId = user.getEmployee().getLineManager().getEmployeeId();
                        return userService.getUserByEmployeeId(lineManagerEmployeeId);
                    }
                    List<AppUser> admins = userService.getUsersByRole("ADMIN");
                    return admins.isEmpty() ? Optional.empty() : Optional.of(admins.get(0));
                })
                .orElse(null);
    }

    private Integer getSLAHoursForConfig(Integer configId) {
        return slaConfigRepository.findById(configId)
                .map(SLAConfiguration::getSlaHours)
                .orElse(24);
    }

    private String getRequestTypePath(String requestType) {
        switch (requestType) {
            case "ASSET_DISPOSAL":
                return "admin/disposal";
            case "DRIVER_REQUEST":
                return "bookings/driver-request";
            case "INFRA_REQUEST":
                return "infra-requests";
            case "RESOURCE_REQUEST":
                return "resources";
            case "BOOKING":
                return "bookings/room";
            case "TRANSFER":
                return "transfers";
            default:
                return "requests";
        }
    }

    private void sendSLANotification(Long userId, String type, String title, String message, String link) {
        try {
            // Create in-app notification
            notificationService.createNotification(userId, type, title, message, link);
            log.debug("✅ In-app notification created for user: {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to create in-app notification: {}", e.getMessage(), e);
        }

        try {
            // Send email
            userService.getUserById(userId).ifPresent(user -> {
                try {
                    String fullLink = baseUrlService.buildUrl(link);
                    emailService.sendSimpleEmail(
                            user.getEmail(),
                            title + " - AssetIQ-Pro",
                            message + "\n\nView details: " + fullLink + "\n\nRegards,\nAssetIQ-Pro Team"
                    );
                    log.debug("✅ Email sent to: {}", user.getEmail());
                } catch (Exception e) {
                    log.error("❌ Failed to send email to {}: {}", user.getEmail(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("❌ Failed to send email notification: {}", e.getMessage(), e);
        }
    }

    // ============================================
    // CONFIGURATION MANAGEMENT
    // ============================================

    @Transactional
    public SLAConfiguration createSLAConfiguration(SLAConfiguration config) {
        log.info("📝 Creating SLA configuration: {}", config.getConfigName());
        return slaConfigRepository.save(config);
    }

    @Transactional
    public void deleteSLAConfiguration(Integer configId) {
        log.info("🗑️ Deleting SLA configuration: {}", configId);

        SLAConfiguration config = slaConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("SLA configuration not found: " + configId));

        // Check if there are any active trackings using this config
        List<RequestSLATracking> trackings = trackingRepository.findBySlaConfigId(configId);
        if (!trackings.isEmpty()) {
            // Instead of deleting, mark as inactive
            config.setIsActive(false);
            slaConfigRepository.save(config);
            log.warn("⚠️ SLA configuration {} has active trackings. Marked as inactive instead of deleting.", configId);
            return;
        }

        slaConfigRepository.delete(config);
        log.info("✅ SLA configuration deleted: {}", configId);
    }

    @Transactional
    public SLAConfiguration updateSLAConfiguration(Integer configId, SLAConfiguration config) {
        log.info("📝 Updating SLA configuration: {}", configId);
        SLAConfiguration existing = slaConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("SLA configuration not found: " + configId));

        existing.setConfigName(config.getConfigName());
        existing.setDescription(config.getDescription());
        existing.setSlaHours(config.getSlaHours());
        existing.setReminderIntervalHours(config.getReminderIntervalHours());
        existing.setEscalationLevels(config.getEscalationLevels());
        existing.setIsActive(config.getIsActive());

        SLAConfiguration updated = slaConfigRepository.save(existing);
        log.info("✅ SLA configuration updated: {}", configId);
        return updated;
    }

    public List<SLAConfiguration> getAllSLAConfigurations() {
        return slaConfigRepository.findAll();
    }

    public List<SLAConfiguration> getSLAConfigurationsByRequestType(String requestType) {
        return slaConfigRepository.findByRequestTypeAndIsActiveTrue(requestType);
    }

    // ============================================
    // DASHBOARD QUERIES
    // ============================================

    public long getSLAComplianceRate() {
        long total = trackingRepository.count();
        if (total == 0) return 100;
        long completed = trackingRepository.countByStatus("COMPLETED");
        return (completed * 100) / total;
    }

    public long getActiveSLACount() {
        return trackingRepository.countByStatus("IN_PROGRESS");
    }

    public long getBreachedSLACount() {
        return trackingRepository.countByStatus("BREACHED");
    }

    public List<RequestSLATracking> getApproachingBreachSLAs() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusHours(2);
        return trackingRepository.findByStatusAndSlaDueAtBetween("IN_PROGRESS", now, threshold);
    }

    public List<RequestSLATracking> getSLAsByRequestType(String requestType) {
        return trackingRepository.findByRequestType(requestType);
    }

    public List<SLAEscalationHistory> getEscalationHistory(Long requestId, String requestType) {
        return escalationHistoryRepository.findByRequestIdAndRequestTypeOrderByEscalatedAtDesc(requestId, requestType);
    }

    public List<SLANotification> getSLANotifications(Long userId, Boolean unreadOnly) {
        if (unreadOnly != null && unreadOnly) {
            return slaNotificationRepository.findByRecipientIdAndReadAtIsNullOrderBySentAtDesc(userId);
        }
        return slaNotificationRepository.findByRecipientIdOrderBySentAtDesc(userId);
    }

    public void markNotificationAsRead(Long notificationId) {
        slaNotificationRepository.findById(notificationId).ifPresent(notif -> {
            notif.setReadAt(LocalDateTime.now());
            slaNotificationRepository.save(notif);
        });
    }

    public void markAllNotificationsAsRead(Long userId) {
        List<SLANotification> notifications = slaNotificationRepository.findByRecipientIdAndReadAtIsNull(userId);
        notifications.forEach(notif -> {
            notif.setReadAt(LocalDateTime.now());
        });
        slaNotificationRepository.saveAll(notifications);
    }

    public List<RequestSLATracking> getAllActiveSLAs() {
        return trackingRepository.findByStatus("IN_PROGRESS");
    }

    public void setEscalationCount(Long trackingId, int count) {
        trackingRepository.findById(trackingId).ifPresent(tracking -> {
            tracking.setEscalationCount(count);
            trackingRepository.save(tracking);
        });
    }

    public long countActiveByRequestType(String requestType) {
        return trackingRepository.countActiveByRequestType(requestType);
    }

    public long countCompletedByRequestType(String requestType) {
        return trackingRepository.countCompletedByRequestType(requestType);
    }

    public long countBreachedByRequestType(String requestType) {
        return trackingRepository.countBreachedByRequestType(requestType);
    }
}