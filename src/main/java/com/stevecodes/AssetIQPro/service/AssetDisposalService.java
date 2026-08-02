package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.AssetDisposalRequest;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Notification;
import com.stevecodes.AssetIQPro.repository.AssetDisposalRequestRepository;
import com.stevecodes.AssetIQPro.repository.AssetRepository;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetDisposalService {

    private final AssetDisposalRequestRepository disposalRequestRepository;
    private final AssetRepository assetRepository;
    private final AppUserService userService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final NotificationRepository notificationRepository;
    private final BaseUrlService baseUrlService;
    private final SLAService slaService;

    // ============================================
    // Disposal Request Management
    // ============================================

    @Transactional
    public AssetDisposalRequest createDisposalRequest(Integer assetId, Long requestedBy,
                                                      String disposalReason, String disposalMethod,
                                                      String priority) {
        log.info("=== CREATE DISPOSAL REQUEST START ===");
        log.info("📝 Creating disposal request for asset: {} by user: {}", assetId, requestedBy);
        log.info("📝 Disposal Reason: {}", disposalReason);
        log.info("📝 Disposal Method: {}", disposalMethod);
        log.info("📝 Priority: {}", priority);

        try {
            Asset asset = assetRepository.findById(assetId)
                    .orElseThrow(() -> {
                        log.error("❌ Asset not found: {}", assetId);
                        return new RuntimeException("Asset not found: " + assetId);
                    });
            log.info("✅ Found asset: {} (Tag: {})", asset.getName(), asset.getTag());

            // Validate EOL
            if (asset.getEolDate() != null && asset.getEolDate().isAfter(LocalDate.now())) {
                log.warn("⚠️ Asset has not reached EOL yet. EOL Date: {}", asset.getEolDate());
                throw new IllegalStateException("Asset has not reached EOL yet. Disposal cannot be initiated.");
            }
            log.info("✅ Asset EOL check passed");

            // Check for pending requests
            if (disposalRequestRepository.existsByAssetIdAndStatusIn(assetId,
                    List.of("PENDING", "APPROVED", "IN_PROGRESS"))) {
                log.warn("⚠️ Asset already has a pending disposal request");
                throw new IllegalStateException("Asset already has a pending disposal request.");
            }
            log.info("✅ No pending requests found for this asset");

            // Create the request
            log.info("📝 Creating disposal request object");
            AssetDisposalRequest request = new AssetDisposalRequest();
            request.setAssetId(assetId);
            request.setRequestedBy(requestedBy);
            request.setDisposalReason(disposalReason);
            request.setDisposalMethod(disposalMethod);
            request.setPriority(priority != null ? priority : "NORMAL");
            request.setStatus("PENDING");
            request.setRequestedAt(LocalDateTime.now());

            log.info("💾 Saving disposal request to database");
            AssetDisposalRequest saved = disposalRequestRepository.save(request);
            log.info("✅ Disposal request saved with ID: {}", saved.getDisposalRequestId());

            // ✅ START SLA TRACKING
            log.info("📊 Starting SLA tracking for disposal request: {}", saved.getDisposalRequestId());
            try {
                slaService.startSLATracking(saved.getDisposalRequestId(), "ASSET_DISPOSAL", requestedBy);
                log.info("✅ SLA tracking started successfully for disposal request: {}", saved.getDisposalRequestId());
            } catch (Exception e) {
                log.error("❌ Failed to start SLA tracking for disposal request: {}", e.getMessage(), e);
                // Don't re-throw - the request is already saved
            }

            // Update asset status
            log.info("📝 Updating asset disposal status to PENDING_DISPOSAL");
            asset.setDisposalStatus("PENDING_DISPOSAL");
            assetRepository.save(asset);
            log.info("✅ Asset status updated");

            // Notify authorizers
            log.info("📧 Notifying disposal authorizers");
            try {
                notifyDisposalAuthorizers(saved);
                log.info("✅ Authorizers notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify authorizers: {}", e.getMessage(), e);
            }

            // Log audit
            try {
                auditService.logAction("DISPOSAL_REQUEST_CREATED",
                        "Disposal request created for asset: " + asset.getTag(),
                        requestedBy);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== CREATE DISPOSAL REQUEST END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Failed to create disposal request: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public AssetDisposalRequest completeDisposalProcess(Long requestId, Long completerId,
                                                        String completionNotes,
                                                        String disposalMethod,
                                                        boolean dataWipeConfirmed) {
        log.info("=== COMPLETE DISPOSAL PROCESS START ===");
        log.info("📝 Completing disposal process for request: {} by: {}", requestId, completerId);
        log.info("📝 Completion Notes: {}", completionNotes);
        log.info("📝 Disposal Method: {}", disposalMethod);
        log.info("📝 Data Wipe Confirmed: {}", dataWipeConfirmed);

        try {
            AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Disposal request not found: {}", requestId);
                        return new RuntimeException("Disposal request not found: " + requestId);
                    });
            log.info("✅ Found request with status: {}", request.getStatus());

            // Validate status
            if (!"IN_PROGRESS".equals(request.getStatus()) && !"APPROVED".equals(request.getStatus())) {
                log.error("❌ Invalid status: {}", request.getStatus());
                throw new IllegalStateException("Request must be in progress or approved. Current: " + request.getStatus());
            }
            log.info("✅ Status validation passed");

            // Handle data wipe
            if (dataWipeConfirmed) {
                log.info("📝 Confirming data wipe");
                request.setDataWipeConfirmed(true);
                request.setDataWipeConfirmedBy(completerId);
                request.setDataWipeConfirmedAt(LocalDateTime.now());
                log.info("✅ Data wipe confirmed");
            }

            // Update request status
            log.info("📝 Updating request status to COMPLETED");
            request.setStatus("COMPLETED");
            request.setCompletedBy(completerId);
            request.setCompletedAt(LocalDateTime.now());
            request.setCompletionNotes(completionNotes);

            log.info("💾 Saving updated request");
            AssetDisposalRequest saved = disposalRequestRepository.save(request);
            log.info("✅ Request saved with status: {}", saved.getStatus());

            // ✅ Complete SLA tracking
            log.info("📊 Completing SLA tracking for disposal request: {}", requestId);
            try {
                slaService.completeSLATracking(requestId, "ASSET_DISPOSAL");
                log.info("✅ SLA tracking completed for disposal request: {}", requestId);
            } catch (Exception e) {
                log.error("❌ Failed to complete SLA tracking for disposal request: {}", e.getMessage(), e);
            }

            // Update asset
            log.info("📝 Updating asset record");
            Asset asset = assetRepository.findById(request.getAssetId())
                    .orElseThrow(() -> {
                        log.error("❌ Asset not found: {}", request.getAssetId());
                        return new RuntimeException("Asset not found");
                    });
            log.info("✅ Found asset: {} (Tag: {})", asset.getName(), asset.getTag());

            asset.setDisposalStatus("DISPOSED");
            asset.setDisposalDate(LocalDate.now());
            asset.setDisposalMethod(disposalMethod);
            asset.setDisposalCompletedBy(completerId);
            asset.setDisposalCompletedAt(LocalDateTime.now());
            asset.setDisposalNotes(completionNotes);
            asset.setDataWipeStatus(dataWipeConfirmed ? "COMPLETED" : "NOT_REQUIRED");
            asset.setAssetLifecycleStatus("DISPOSED");
            asset.setStatus(Asset.AssetStatus.RETIRED);

            assetRepository.save(asset);
            log.info("✅ Asset updated with disposal status: DISPOSED");

            // Generate certificate
            log.info("📄 Generating disposal certificate");
            try {
                generateDisposalCertificate(requestId);
                log.info("✅ Disposal certificate generated");
            } catch (Exception e) {
                log.error("❌ Failed to generate disposal certificate: {}", e.getMessage(), e);
            }

            // Notify requester
            log.info("📧 Notifying requester");
            try {
                notifyRequester(request, "Your disposal request has been completed.");
                log.info("✅ Requester notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify requester: {}", e.getMessage(), e);
            }

            // Log audit
            try {
                auditService.logAction("DISPOSAL_PROCESS_COMPLETED",
                        "Disposal process completed for request: " + requestId,
                        completerId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== COMPLETE DISPOSAL PROCESS END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Failed to complete disposal process: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void confirmDataWipe(Long requestId, Long confirmerId) {
        log.info("=== CONFIRM DATA WIPE START ===");
        log.info("📝 Confirming data wipe for request: {} by: {}", requestId, confirmerId);

        try {
            AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Disposal request not found: {}", requestId);
                        return new RuntimeException("Disposal request not found: " + requestId);
                    });
            log.info("✅ Found request with status: {}", request.getStatus());

            if (!"IN_PROGRESS".equals(request.getStatus()) && !"APPROVED".equals(request.getStatus())) {
                log.error("❌ Invalid status: {}", request.getStatus());
                throw new IllegalStateException("Request must be in progress or approved. Current: " + request.getStatus());
            }
            log.info("✅ Status validation passed");

            request.setDataWipeConfirmed(true);
            request.setDataWipeConfirmedBy(confirmerId);
            request.setDataWipeConfirmedAt(LocalDateTime.now());
            disposalRequestRepository.save(request);
            log.info("✅ Data wipe confirmed for request: {}", requestId);

            // Update asset
            Asset asset = assetRepository.findById(request.getAssetId())
                    .orElseThrow(() -> {
                        log.error("❌ Asset not found: {}", request.getAssetId());
                        return new RuntimeException("Asset not found");
                    });
            log.info("✅ Found asset: {} (Tag: {})", asset.getName(), asset.getTag());

            asset.setDataWipeStatus("VERIFIED");
            asset.setDataWipeVerifiedBy(confirmerId);
            asset.setDataWipeVerifiedAt(LocalDateTime.now());
            assetRepository.save(asset);
            log.info("✅ Asset data wipe status updated to VERIFIED");

            try {
                auditService.logAction("DATA_WIPE_CONFIRMED",
                        "Data wipe confirmed for disposal request: " + requestId,
                        confirmerId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== CONFIRM DATA WIPE END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to confirm data wipe: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // Query Methods
    // ============================================

    public List<AssetDisposalRequest> getPendingRequests() {
        log.debug("📋 Getting pending disposal requests");
        return disposalRequestRepository.findByStatus("PENDING");
    }

    public List<AssetDisposalRequest> getRequestsByStatus(String status) {
        log.debug("📋 Getting disposal requests with status: {}", status);
        return disposalRequestRepository.findByStatus(status);
    }

    public List<AssetDisposalRequest> getRequestsByAssetId(Integer assetId) {
        log.debug("📋 Getting disposal requests for asset: {}", assetId);
        return disposalRequestRepository.findByAssetId(assetId);
    }

    public List<AssetDisposalRequest> getRequestsByRequester(Long userId) {
        log.debug("📋 Getting disposal requests for requester: {}", userId);
        return disposalRequestRepository.findByRequestedBy(userId);
    }

    public List<Asset> getAssetsReadyForDisposal() {
        LocalDate today = LocalDate.now();
        log.debug("📋 Getting assets ready for disposal (EOL before: {})", today);
        return assetRepository.findByEolDateBeforeAndDisposalStatus(today, "ACTIVE");
    }

    public List<Asset> getAssetsPastRetentionPeriod() {
        LocalDate today = LocalDate.now();
        log.debug("📋 Getting assets past retention period (before: {})", today);
        return assetRepository.findByRetentionPeriodEndDateBeforeAndDisposalStatus(today, "ACTIVE");
    }

    public AssetDisposalRequest getRequestById(Long requestId) {
        log.debug("📋 Getting disposal request by ID: {}", requestId);
        return disposalRequestRepository.findById(requestId)
                .orElseThrow(() -> {
                    log.error("❌ Disposal request not found: {}", requestId);
                    return new RuntimeException("Disposal request not found: " + requestId);
                });
    }

    public boolean hasPendingRequest(Integer assetId) {
        log.debug("📋 Checking for pending request on asset: {}", assetId);
        return disposalRequestRepository.existsByAssetIdAndStatusIn(assetId,
                List.of("PENDING", "APPROVED", "IN_PROGRESS"));
    }

    public AssetDisposalRequest getPendingRequestByAssetId(Integer assetId) {
        log.debug("📋 Getting pending request for asset: {}", assetId);
        List<AssetDisposalRequest> requests = disposalRequestRepository
                .findByAssetIdAndStatusIn(assetId, List.of("PENDING", "APPROVED", "IN_PROGRESS"));
        return requests.isEmpty() ? null : requests.get(0);
    }

    // ============================================
    // Missing Methods - Added with Debug Logging
    // ============================================

    @Transactional
    public void approveDisposalRequest(Long requestId, Long userId, String comment, String approvalReference) {
        log.info("=== APPROVE DISPOSAL REQUEST START ===");
        log.info("📝 Approving disposal request: {} by user: {}", requestId, userId);
        log.info("📝 Comment: {}", comment);
        log.info("📝 Approval Reference: {}", approvalReference);

        try {
            AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Disposal request not found: {}", requestId);
                        return new RuntimeException("Disposal request not found: " + requestId);
                    });
            log.info("✅ Found request with status: {}", request.getStatus());

            if (!"PENDING".equals(request.getStatus())) {
                log.error("❌ Invalid status for approval: {}", request.getStatus());
                throw new IllegalStateException("Request must be pending. Current: " + request.getStatus());
            }

            request.setStatus("APPROVED");
            request.setApprovedBy(userId);
            request.setApprovedAt(LocalDateTime.now());
            request.setApprovalComment(comment);
            request.setApprovalReference(approvalReference);

            disposalRequestRepository.save(request);
            log.info("✅ Disposal request approved");

            try {
                auditService.logAction("DISPOSAL_REQUEST_APPROVED",
                        "Disposal request approved: " + requestId,
                        userId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== APPROVE DISPOSAL REQUEST END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to approve disposal request: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void rejectDisposalRequest(Long requestId, Long userId, String reason) {
        log.info("=== REJECT DISPOSAL REQUEST START ===");
        log.info("📝 Rejecting disposal request: {} by user: {}", requestId, userId);
        log.info("📝 Reason: {}", reason);

        try {
            AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Disposal request not found: {}", requestId);
                        return new RuntimeException("Disposal request not found: " + requestId);
                    });
            log.info("✅ Found request with status: {}", request.getStatus());

            if (!"PENDING".equals(request.getStatus())) {
                log.error("❌ Invalid status for rejection: {}", request.getStatus());
                throw new IllegalStateException("Request must be pending. Current: " + request.getStatus());
            }

            request.setStatus("REJECTED");
            request.setRejectionReason(reason);
            request.setApprovedBy(userId);
            request.setApprovedAt(LocalDateTime.now());

            disposalRequestRepository.save(request);
            log.info("✅ Disposal request rejected");

            try {
                auditService.logAction("DISPOSAL_REQUEST_REJECTED",
                        "Disposal request rejected: " + requestId + " - Reason: " + reason,
                        userId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== REJECT DISPOSAL REQUEST END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to reject disposal request: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void startDisposalProcess(Long requestId, Long userId) {
        log.info("=== START DISPOSAL PROCESS START ===");
        log.info("📝 Starting disposal process for request: {} by user: {}", requestId, userId);

        try {
            AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Disposal request not found: {}", requestId);
                        return new RuntimeException("Disposal request not found: " + requestId);
                    });
            log.info("✅ Found request with status: {}", request.getStatus());

            if (!"APPROVED".equals(request.getStatus())) {
                log.error("❌ Invalid status for starting process: {}", request.getStatus());
                throw new IllegalStateException("Request must be approved. Current: " + request.getStatus());
            }

            request.setStatus("IN_PROGRESS");
            request.setCompletedBy(userId);
            request.setCompletedAt(LocalDateTime.now());

            disposalRequestRepository.save(request);
            log.info("✅ Disposal process started");

            // Update asset
            Asset asset = assetRepository.findById(request.getAssetId())
                    .orElseThrow(() -> {
                        log.error("❌ Asset not found: {}", request.getAssetId());
                        return new RuntimeException("Asset not found");
                    });
            log.info("✅ Found asset: {} (Tag: {})", asset.getName(), asset.getTag());

            asset.setDisposalStatus("IN_PROGRESS");
            assetRepository.save(asset);
            log.info("✅ Asset status updated to IN_PROGRESS");

            try {
                auditService.logAction("DISPOSAL_PROCESS_STARTED",
                        "Disposal process started for request: " + requestId,
                        userId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== START DISPOSAL PROCESS END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to start disposal process: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // Helper Methods
    // ============================================

    private void notifyDisposalAuthorizers(AssetDisposalRequest request) {
        log.info("📧 Notifying disposal authorizers about request: {}", request.getDisposalRequestId());

        try {
            String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
            String assetName = getAssetName(request.getAssetId());

            List<AppUser> authorizers = userService.getUsersWithPermission("DISPOSAL_APPROVE");

            if (authorizers.isEmpty()) {
                log.warn("⚠️ No users with DISPOSAL_APPROVE permission found. Falling back to admins.");
                authorizers = userService.getUsersByRole("ADMIN");
            }

            log.info("📧 Found {} authorizers to notify", authorizers.size());

            for (AppUser user : authorizers) {
                try {
                    emailService.sendSimpleEmail(
                            user.getEmail(),
                            "New Asset Disposal Request - Action Required",
                            generateDisposalRequestEmailBody(request, assetName, requestLink)
                    );
                    log.info("✅ Email sent to: {}", user.getEmail());

                    createNotification(
                            user.getUserId(),
                            "DISPOSAL_REQUEST_PENDING",
                            "New Disposal Request",
                            "A disposal request for asset " + assetName + " requires your approval.",
                            requestLink
                    );
                    log.info("✅ Notification created for user: {}", user.getUserId());

                } catch (Exception e) {
                    log.error("❌ Failed to notify user {}: {}", user.getUserId(), e.getMessage(), e);
                }
            }

            log.info("✅ Notified {} authorizers", authorizers.size());

        } catch (Exception e) {
            log.error("❌ Failed to notify authorizers: {}", e.getMessage(), e);
        }
    }

    private void notifyRequester(AssetDisposalRequest request, String message) {
        log.info("📧 Notifying requester {} about request: {}", request.getRequestedBy(), request.getDisposalRequestId());

        try {
            String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
            String assetName = getAssetName(request.getAssetId());

            userService.getUserById(request.getRequestedBy()).ifPresentOrElse(
                    user -> {
                        try {
                            emailService.sendSimpleEmail(
                                    user.getEmail(),
                                    "Disposal Request Update - #" + request.getDisposalRequestId(),
                                    "Dear " + user.getFullName() + ",\n\n" +
                                            "Your disposal request for asset " + assetName + " has been updated.\n" +
                                            "Status: " + request.getStatus() + "\n" +
                                            "Message: " + message + "\n\n" +
                                            "View details: " + requestLink + "\n\n" +
                                            "Thank you,\nAssetIQ-Pro Team"
                            );
                            log.info("✅ Email sent to requester: {}", user.getEmail());

                            createNotification(
                                    user.getUserId(),
                                    "DISPOSAL_REQUEST_UPDATE",
                                    "Disposal Request Update",
                                    message + " (Request #" + request.getDisposalRequestId() + ")",
                                    requestLink
                            );
                            log.info("✅ Notification created for requester: {}", user.getUserId());

                        } catch (Exception e) {
                            log.error("❌ Failed to send notification to requester: {}", e.getMessage(), e);
                        }
                    },
                    () -> log.warn("⚠️ Requester not found with ID: {}", request.getRequestedBy())
            );

        } catch (Exception e) {
            log.error("❌ Failed to notify requester: {}", e.getMessage(), e);
        }
    }

    private void generateDisposalCertificate(Long requestId) {
        log.info("📄 Generating disposal certificate for request: {}", requestId);

        try {
            AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Disposal request not found: {}", requestId);
                        return new RuntimeException("Disposal request not found: " + requestId);
                    });

            Asset asset = assetRepository.findById(request.getAssetId())
                    .orElseThrow(() -> {
                        log.error("❌ Asset not found: {}", request.getAssetId());
                        return new RuntimeException("Asset not found");
                    });

            log.info("📄 Disposal certificate generated for request: {} (Asset: {})", requestId, asset.getTag());

            // Send certificate to requester
            userService.getUserById(request.getRequestedBy()).ifPresent(user -> {
                try {
                    emailService.sendSimpleEmail(
                            user.getEmail(),
                            "Disposal Certificate - Asset #" + asset.getTag(),
                            "Dear " + user.getFullName() + ",\n\n" +
                                    "A disposal certificate has been generated for asset " + asset.getTag() + ".\n" +
                                    "Asset: " + asset.getName() + "\n" +
                                    "Serial: " + asset.getSerialNumber() + "\n" +
                                    "Disposal Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "\n" +
                                    "Disposal Method: " + request.getDisposalMethod() + "\n\n" +
                                    "Please contact IT Infrastructure for the official certificate.\n\n" +
                                    "Thank you,\nAssetIQ-Pro Team"
                    );
                    log.info("✅ Certificate email sent to: {}", user.getEmail());
                } catch (Exception e) {
                    log.error("❌ Failed to send certificate email: {}", e.getMessage(), e);
                }
            });

        } catch (Exception e) {
            log.error("❌ Failed to generate disposal certificate: {}", e.getMessage(), e);
        }
    }

    private void createNotification(Long userId, String type, String title, String message, String link) {
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setLink(link != null ? link : "/admin/disposal");
            notification.setRead(false);
            notification.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
            log.debug("✅ Notification created for user: {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to create notification for user {}: {}", userId, e.getMessage(), e);
        }
    }

    private String getAssetName(Integer assetId) {
        try {
            return assetRepository.findById(assetId)
                    .map(Asset::getName)
                    .orElse("Asset #" + assetId);
        } catch (Exception e) {
            return "Asset #" + assetId;
        }
    }

    private String getRequesterName(Long userId) {
        try {
            return userService.getUserById(userId)
                    .map(AppUser::getFullName)
                    .orElse("User #" + userId);
        } catch (Exception e) {
            return "User #" + userId;
        }
    }

    private String generateDisposalRequestEmailBody(AssetDisposalRequest request, String assetName, String requestLink) {
        return "A new asset disposal request has been submitted.\n\n" +
                "Request #: " + request.getDisposalRequestId() + "\n" +
                "Asset: " + assetName + "\n" +
                "Disposal Reason: " + request.getDisposalReason() + "\n" +
                "Disposal Method: " + request.getDisposalMethod() + "\n" +
                "Priority: " + request.getPriority() + "\n" +
                "Requested By: " + getRequesterName(request.getRequestedBy()) + "\n" +
                "Requested At: " + request.getRequestedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n\n" +
                "Please review and respond: " + requestLink + "\n\n" +
                "Regards,\nAssetIQ-Pro System";
    }
}