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

    @Transactional
    public RequestSLATracking startSLATracking(Long requestId, String requestType, Long userId) {
        log.info("Starting SLA tracking for request: {} (Type: {})", requestId, requestType);

        SLAConfiguration slaConfig = getSLAConfigurationForRequest(requestType);

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

        RequestSLATracking saved = trackingRepository.save(tracking);

        sendSLANotification(
                userId,
                "SLA_STARTED",
                "SLA Started",
                "Your request (ID: " + requestId + ") has been created. SLA: " + slaConfig.getSlaHours() + " hours.",
                "/" + getRequestTypePath(requestType) + "/" + requestId
        );

        auditService.logAction("SLA_STARTED",
                "SLA started for " + requestType + " request #" + requestId + " with SLA of " + slaConfig.getSlaHours() + " hours",
                userId);

        return saved;
    }

    private SLAConfiguration getSLAConfigurationForRequest(String requestType) {
        return slaConfigRepository.findByRequestTypeAndIsDefaultTrue(requestType)
                .orElseGet(() -> {
                    return slaConfigRepository.findFirstByRequestTypeAndIsActiveTrue(requestType)
                            .orElseThrow(() -> new RuntimeException("No SLA configuration found for request type: " + requestType));
                });
    }

    public RequestSLATracking getSLAStatus(Long requestId, String requestType) {
        return trackingRepository.findByRequestIdAndRequestType(requestId, requestType)
                .orElse(null);
    }

    @Transactional
    public void completeSLATracking(Long requestId, String requestType) {
        log.info("Completing SLA tracking for request: {} (Type: {})", requestId, requestType);

        trackingRepository.findByRequestIdAndRequestType(requestId, requestType)
                .ifPresent(tracking -> {
                    tracking.setStatus("COMPLETED");
                    tracking.setCompletedAt(LocalDateTime.now());
                    trackingRepository.save(tracking);
                });
    }

    // ============================================
    // SLA CHECKING (Scheduled Job)
    // ============================================

    @Scheduled(cron = "0 0/30 * * * *")
    @Transactional
    public void checkAllSLAs() {
        log.info("Running SLA check for all request types");

        List<RequestSLATracking> trackings = trackingRepository.findByStatus("IN_PROGRESS");

        for (RequestSLATracking tracking : trackings) {
            processSLACheck(tracking);
        }
    }

    private void processSLACheck(RequestSLATracking tracking) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueAt = tracking.getSlaDueAt();
        long totalHours = ChronoUnit.HOURS.between(tracking.getSlaStartedAt(), dueAt);
        long elapsedHours = ChronoUnit.HOURS.between(tracking.getSlaStartedAt(), now);
        double percentageElapsed = totalHours > 0 ? (double) elapsedHours / totalHours * 100 : 0;

        if (now.isAfter(dueAt)) {
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
        log.warn("SLA BREACHED! Request: {} (Type: {})", tracking.getRequestId(), tracking.getRequestType());

        tracking.setStatus("BREACHED");
        tracking.setBreachesCount(tracking.getBreachesCount() + 1);
        trackingRepository.save(tracking);

        Long ownerId = getRequestOwner(tracking.getRequestId(), tracking.getRequestType());
        AppUser owner = userService.getUserById(ownerId).orElse(null);
        AppUser manager = getLineManager(ownerId);

        String breachMessage = String.format(
                "SLA BREACHED! Request #%d (%s) has exceeded its SLA of %d hours.",
                tracking.getRequestId(),
                tracking.getRequestType(),
                getSLAHoursForConfig(tracking.getSlaConfigId())
        );

        if (owner != null) {
            sendSLANotification(
                    owner.getUserId(),
                    "SLA_BREACHED",
                    "SLA Breached",
                    breachMessage + " Please take immediate action.",
                    "/" + getRequestTypePath(tracking.getRequestType()) + "/" + tracking.getRequestId()
            );
        }

        if (manager != null) {
            sendSLANotification(
                    manager.getUserId(),
                    "SLA_BREACHED_ESCALATED",
                    "SLA Breached - Employee Alert",
                    "Employee: " + (owner != null ? owner.getFullName() : "Unknown") + "\n" + breachMessage,
                    "/" + getRequestTypePath(tracking.getRequestType()) + "/" + tracking.getRequestId()
            );
        }

        SLAEscalationHistory escalation = new SLAEscalationHistory();
        escalation.setRequestId(tracking.getRequestId());
        escalation.setRequestType(tracking.getRequestType());
        escalation.setSlaRuleId(tracking.getSlaConfigId());
        escalation.setEscalationLevel(tracking.getEscalationCount() + 1);
        escalation.setEscalatedTo(manager != null ? manager.getUserId() : ownerId);
        escalation.setEscalatedBy(1L);
        escalation.setEscalatedAt(LocalDateTime.now());
        escalation.setNotificationSent(true);
        escalation.setReason("SLA Breached after " + getSLAHoursForConfig(tracking.getSlaConfigId()) + " hours");
        escalationHistoryRepository.save(escalation);

        auditService.logAction("SLA_BREACHED",
                "SLA breached for " + tracking.getRequestType() + " request #" + tracking.getRequestId(),
                ownerId);
    }

    // ============================================
    // SLA ESCALATION
    // ============================================

    private void escalateSLA(RequestSLATracking tracking) {
        log.info("Escalating SLA for request: {} (Type: {})", tracking.getRequestId(), tracking.getRequestType());

        Long ownerId = getRequestOwner(tracking.getRequestId(), tracking.getRequestType());
        AppUser manager = getLineManager(ownerId);

        if (manager == null) {
            log.warn("No manager found for escalation. Request: {}", tracking.getRequestId());
            return;
        }

        tracking.setEscalatedTo(manager.getUserId());
        tracking.setEscalationCount(tracking.getEscalationCount() + 1);
        trackingRepository.save(tracking);

        SLAEscalationHistory escalation = new SLAEscalationHistory();
        escalation.setRequestId(tracking.getRequestId());
        escalation.setRequestType(tracking.getRequestType());
        escalation.setSlaRuleId(tracking.getSlaConfigId());
        escalation.setEscalationLevel(tracking.getEscalationCount());
        escalation.setEscalatedTo(manager.getUserId());
        escalation.setEscalatedBy(1L);
        escalation.setEscalatedAt(LocalDateTime.now());
        escalation.setNotificationSent(true);
        escalation.setReason("SLA approaching breach. Request pending for " +
                ChronoUnit.HOURS.between(tracking.getSlaStartedAt(), LocalDateTime.now()) + " hours");
        escalationHistoryRepository.save(escalation);

        String escalationMessage = String.format(
                "SLA Escalation: Request #%d (%s) has been pending for %d hours. Please review and take action.",
                tracking.getRequestId(),
                tracking.getRequestType(),
                ChronoUnit.HOURS.between(tracking.getSlaStartedAt(), LocalDateTime.now())
        );

        sendSLANotification(
                manager.getUserId(),
                "SLA_ESCALATED",
                "SLA Escalated - Action Required",
                escalationMessage,
                "/" + getRequestTypePath(tracking.getRequestType()) + "/" + tracking.getRequestId()
        );

        auditService.logAction("SLA_ESCALATED",
                "SLA escalated for " + tracking.getRequestType() + " request #" + tracking.getRequestId() + " to " + manager.getUsername(),
                ownerId);
    }

    // ============================================
    // SLA REMINDERS
    // ============================================

    private void sendSLAReminder(RequestSLATracking tracking, String percentage) {
        Long ownerId = getRequestOwner(tracking.getRequestId(), tracking.getRequestType());
        AppUser owner = userService.getUserById(ownerId).orElse(null);

        if (owner == null) return;

        String reminderMessage = String.format(
                "SLA Reminder: Request #%d (%s) has used %s%% of its SLA time. Due at: %s",
                tracking.getRequestId(),
                tracking.getRequestType(),
                percentage,
                tracking.getSlaDueAt().toString()
        );

        sendSLANotification(
                owner.getUserId(),
                "SLA_REMINDER",
                "SLA Reminder - " + percentage + "% Elapsed",
                reminderMessage,
                "/" + getRequestTypePath(tracking.getRequestType()) + "/" + tracking.getRequestId()
        );

        if ("75".equals(percentage) || "90".equals(percentage)) {
            AppUser manager = getLineManager(ownerId);
            if (manager != null) {
                sendSLANotification(
                        manager.getUserId(),
                        "SLA_REMINDER_MANAGER",
                        "SLA Reminder - Employee Request at " + percentage + "%",
                        "Employee: " + owner.getFullName() + "\n" + reminderMessage,
                        "/" + getRequestTypePath(tracking.getRequestType()) + "/" + tracking.getRequestId()
                );
            }
        }

        tracking.setLastReminderSentAt(LocalDateTime.now());
        trackingRepository.save(tracking);
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private Long getRequestOwner(Long requestId, String requestType) {
        // This should be extended for each request type
        // For now, return admin as fallback
        log.warn("getRequestOwner not fully implemented for request type: {}. Using admin fallback.", requestType);
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
        // Create in-app notification
        notificationService.createNotification(userId, type, title, message, link);

        // Send email
        userService.getUserById(userId).ifPresent(user -> {
            String fullLink = baseUrlService.buildUrl(link);
            emailService.sendSimpleEmail(
                    user.getEmail(),
                    title + " - AssetIQ-Pro",
                    message + "\n\nView details: " + fullLink + "\n\nRegards,\nAssetIQ-Pro Team"
            );
        });
    }

    // ============================================
    // CONFIGURATION MANAGEMENT
    // ============================================

    @Transactional
    public SLAConfiguration createSLAConfiguration(SLAConfiguration config) {
        log.info("Creating SLA configuration: {}", config.getConfigName());
        return slaConfigRepository.save(config);
    }

    @Transactional
    public void deleteSLAConfiguration(Integer configId) {
        log.info("Deleting SLA configuration: {}", configId);

        SLAConfiguration config = slaConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("SLA configuration not found: " + configId));

        // Check if there are any active trackings using this config
        List<RequestSLATracking> trackings = trackingRepository.findBySlaConfigId(configId);
        if (!trackings.isEmpty()) {
            // Instead of deleting, mark as inactive
            config.setIsActive(false);
            slaConfigRepository.save(config);
            log.warn("SLA configuration {} has active trackings. Marked as inactive instead of deleting.", configId);
            return;
        }

        slaConfigRepository.delete(config);
    }

    @Transactional
    public SLAConfiguration updateSLAConfiguration(Integer configId, SLAConfiguration config) {
        log.info("Updating SLA configuration: {}", configId);
        SLAConfiguration existing = slaConfigRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("SLA configuration not found: " + configId));

        existing.setConfigName(config.getConfigName());
        existing.setDescription(config.getDescription());
        existing.setSlaHours(config.getSlaHours());
        existing.setReminderIntervalHours(config.getReminderIntervalHours());
        existing.setEscalationLevels(config.getEscalationLevels());
        existing.setIsActive(config.getIsActive());

        return slaConfigRepository.save(existing);
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