package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.AssetHistory;
import com.stevecodes.AssetIQPro.entity.Notification;
import com.stevecodes.AssetIQPro.repository.AssetHistoryRepository;
import com.stevecodes.AssetIQPro.repository.AssetRepository;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetLifecycleService {

    private final AssetRepository assetRepository;
    private final AssetHistoryRepository historyRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    // ============================================
    // Scheduled Jobs
    // ============================================

    @Scheduled(cron = "0 0 6 * * *") // Run daily at 6 AM
    @Transactional
    public void checkWarrantyExpiry() {
        log.info("Running warranty expiry check...");

        List<Asset> assets = assetRepository.findAll();
        for (Asset asset : assets) {
            if (asset.getWarrantyEndDate() != null) {
                int daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(), asset.getWarrantyEndDate());
                int notificationDays = asset.getWarrantyNotificationDays() != null ?
                        asset.getWarrantyNotificationDays() : 30;

                if (daysLeft <= notificationDays && daysLeft >= 0) {
                    sendWarrantyAlert(asset, daysLeft);
                } else if (daysLeft < 0) {
                    // Warranty expired
                    sendWarrantyExpiredAlert(asset);
                }
            }
        }
    }

    @Scheduled(cron = "0 0 6 * * *") // Run daily at 6 AM
    @Transactional
    public void checkEOL() {
        log.info("Running EOL check...");

        List<Asset> assets = assetRepository.findAll();
        for (Asset asset : assets) {
            if (asset.getEolDate() != null) {
                int daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(), asset.getEolDate());
                int notificationDays = asset.getEolNotificationDays() != null ?
                        asset.getEolNotificationDays() : 30;

                if (daysLeft <= notificationDays && daysLeft >= 0) {
                    sendEOLAlert(asset, daysLeft);
                } else if (daysLeft < 0) {
                    // EOL passed
                    sendEOLExpiredAlert(asset);
                }
            }
        }
    }

    // ============================================
    // Alert Methods
    // ============================================

    private void sendWarrantyAlert(Asset asset, int daysLeft) {
        String message = String.format(
                "Asset %s (%s) warranty expires in %d days on %s.",
                asset.getTag(), asset.getName(), daysLeft, asset.getWarrantyEndDate()
        );

        createNotification(asset, "WARRANTY_EXPIRY", "Warranty Expiring Soon", message);

        emailService.sendWarrantyExpiryAlert(
                "asset.manager@company.com",
                asset.getTag(),
                asset.getWarrantyEndDate().toString(),
                daysLeft
        );

        log.info("Warranty alert sent for asset: {}", asset.getTag());
    }

    private void sendWarrantyExpiredAlert(Asset asset) {
        String message = String.format(
                "Warranty for asset %s (%s) has expired on %s. Please take action.",
                asset.getTag(), asset.getName(), asset.getWarrantyEndDate()
        );

        createNotification(asset, "WARRANTY_EXPIRED", "Warranty Expired", message);

        // Create history entry
        createHistory(asset, "WARRANTY_EXPIRED", "Warranty expired on " + asset.getWarrantyEndDate());
    }

    private void sendEOLAlert(Asset asset, int daysLeft) {
        String message = String.format(
                "Asset %s (%s) reaches End of Life in %d days on %s.",
                asset.getTag(), asset.getName(), daysLeft, asset.getEolDate()
        );

        createNotification(asset, "EOL", "End of Life Approaching", message);

        emailService.sendEOLAlert(
                "asset.manager@company.com",
                asset.getTag(),
                asset.getEolDate().toString(),
                daysLeft
        );

        log.info("EOL alert sent for asset: {}", asset.getTag());
    }

    private void sendEOLExpiredAlert(Asset asset) {
        String message = String.format(
                "Asset %s (%s) has reached End of Life on %s. Please plan for replacement.",
                asset.getTag(), asset.getName(), asset.getEolDate()
        );

        createNotification(asset, "EOL_EXPIRED", "End of Life Reached", message);

        createHistory(asset, "EOL_REACHED", "Asset reached End of Life on " + asset.getEolDate());
    }

    // ============================================
    // Helper Methods
    // ============================================

    private void createNotification(Asset asset, String type, String title, String message) {
        // Find users who should receive this notification
        // This could be the asset owner, department manager, etc.
        Long userId = 1L; // Placeholder - get from asset assignment

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setLink("/assets/" + asset.getAssetId());
        notificationRepository.save(notification);
    }

    private void createHistory(Asset asset, String eventType, String details) {
        AssetHistory history = new AssetHistory();
        history.setAssetId(asset.getAssetId());
        history.setEventType(eventType);
        history.setDetails(details);
        history.setEventDate(LocalDateTime.now());
        historyRepository.save(history);
    }

    // ============================================
    // Manual Methods
    // ============================================

    @Transactional
    public void updateAssetLifecycle(Integer assetId, Integer warrantyYears, LocalDate eolDate,
                                     Integer eolNotificationDays, Integer warrantyNotificationDays) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("Asset not found: " + assetId));

        if (warrantyYears != null) {
            asset.setWarrantyYears(warrantyYears);
            if (asset.getPurchaseDate() != null) {
                asset.setWarrantyEndDate(asset.getPurchaseDate().plusYears(warrantyYears));
            }
        }

        if (eolDate != null) {
            asset.setEolDate(eolDate);
        }

        if (eolNotificationDays != null) {
            asset.setEolNotificationDays(eolNotificationDays);
        }

        if (warrantyNotificationDays != null) {
            asset.setWarrantyNotificationDays(warrantyNotificationDays);
        }

        assetRepository.save(asset);

        auditService.logAction("ASSET_LIFECYCLE_UPDATED",
                "Lifecycle updated for asset " + asset.getTag(),
                null);
    }

    public long countAssetsWithWarrantyExpiringSoon() {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(30);
        return assetRepository.findAssetsWithWarrantyExpiringWithinDays(today, threshold).size();
    }

    public long countAssetsWithEOLSoon() {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(30);
        return assetRepository.findAssetsWithEOLWithinDays(today, threshold).size();
    }
}
