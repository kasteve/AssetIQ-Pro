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

    // ============================================
    // Disposal Request Management
    // ============================================

    @Transactional
    public AssetDisposalRequest createDisposalRequest(Integer assetId, Long requestedBy,
                                                      String disposalReason, String disposalMethod,
                                                      String priority) {
        log.info("Creating disposal request for asset: {} by user: {}", assetId, requestedBy);

        // ✅ FIXED: Use Integer for assetId
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("Asset not found: " + assetId));

        // Check if asset is eligible for disposal
        if (asset.getEolDate() != null && asset.getEolDate().isAfter(LocalDate.now())) {
            throw new IllegalStateException("Asset has not reached EOL yet. Disposal cannot be initiated.");
        }

        // Check if there's already a pending disposal request
        // ✅ FIXED: Use Integer for assetId
        if (disposalRequestRepository.existsByAssetIdAndStatusIn(assetId,
                List.of("PENDING", "APPROVED", "IN_PROGRESS"))) {
            throw new IllegalStateException("Asset already has a pending disposal request.");
        }

        AssetDisposalRequest request = new AssetDisposalRequest();
        request.setAssetId(assetId);
        request.setRequestedBy(requestedBy);
        request.setDisposalReason(disposalReason);
        request.setDisposalMethod(disposalMethod);
        request.setPriority(priority != null ? priority : "NORMAL");
        request.setStatus("PENDING");
        request.setRequestedAt(LocalDateTime.now());

        AssetDisposalRequest saved = disposalRequestRepository.save(request);

        // Update asset status
        asset.setDisposalStatus("PENDING_DISPOSAL");
        assetRepository.save(asset);

        // Notify authorized personnel
        notifyDisposalAuthorizers(saved);

        auditService.logAction("DISPOSAL_REQUEST_CREATED",
                "Disposal request created for asset: " + asset.getTag(),
                requestedBy);

        return saved;
    }

    @Transactional
    public AssetDisposalRequest approveDisposalRequest(Long requestId, Long approverId,
                                                       String comment, String approvalReference) {
        log.info("Approving disposal request: {} by: {}", requestId, approverId);

        AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Disposal request not found: " + requestId));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not in pending status. Current: " + request.getStatus());
        }

        request.setStatus("APPROVED");
        request.setApprovedBy(approverId);
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovalComment(comment);

        AssetDisposalRequest saved = disposalRequestRepository.save(request);

        // Update asset
        // ✅ FIXED: Use Integer for assetId
        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("Asset not found"));
        asset.setDisposalStatus("APPROVED");
        asset.setDisposalAuthorizedBy(approverId);
        asset.setDisposalAuthorizedAt(LocalDateTime.now());
        asset.setDisposalApprovalReference(approvalReference);
        assetRepository.save(asset);

        // Notify requester
        notifyRequester(request, "Your disposal request has been approved.");

        auditService.logAction("DISPOSAL_REQUEST_APPROVED",
                "Disposal request approved: " + requestId,
                approverId);

        return saved;
    }

    @Transactional
    public AssetDisposalRequest rejectDisposalRequest(Long requestId, Long approverId, String reason) {
        log.info("Rejecting disposal request: {} by: {}", requestId, approverId);

        AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Disposal request not found: " + requestId));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not in pending status. Current: " + request.getStatus());
        }

        request.setStatus("REJECTED");
        request.setApprovedBy(approverId);
        request.setApprovedAt(LocalDateTime.now());
        request.setRejectionReason(reason);

        AssetDisposalRequest saved = disposalRequestRepository.save(request);

        // Reset asset status
        // ✅ FIXED: Use Integer for assetId
        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("Asset not found"));
        asset.setDisposalStatus("ACTIVE");
        assetRepository.save(asset);

        notifyRequester(request, "Your disposal request has been rejected. Reason: " + reason);

        auditService.logAction("DISPOSAL_REQUEST_REJECTED",
                "Disposal request rejected: " + requestId,
                approverId);

        return saved;
    }

    @Transactional
    public AssetDisposalRequest startDisposalProcess(Long requestId, Long executorId) {
        log.info("Starting disposal process for request: {} by: {}", requestId, executorId);

        AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Disposal request not found: " + requestId));

        if (!"APPROVED".equals(request.getStatus())) {
            throw new IllegalStateException("Request must be approved before disposal. Current: " + request.getStatus());
        }

        request.setStatus("IN_PROGRESS");
        AssetDisposalRequest saved = disposalRequestRepository.save(request);

        // Update asset
        // ✅ FIXED: Use Integer for assetId
        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("Asset not found"));
        asset.setDisposalStatus("IN_PROGRESS");
        assetRepository.save(asset);

        auditService.logAction("DISPOSAL_PROCESS_STARTED",
                "Disposal process started for request: " + requestId,
                executorId);

        return saved;
    }

    @Transactional
    public AssetDisposalRequest completeDisposalProcess(Long requestId, Long completerId,
                                                        String completionNotes,
                                                        String disposalMethod,
                                                        boolean dataWipeConfirmed) {
        log.info("Completing disposal process for request: {} by: {}", requestId, completerId);

        AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Disposal request not found: " + requestId));

        if (!"IN_PROGRESS".equals(request.getStatus()) && !"APPROVED".equals(request.getStatus())) {
            throw new IllegalStateException("Request must be in progress or approved. Current: " + request.getStatus());
        }

        if (dataWipeConfirmed) {
            request.setDataWipeConfirmed(true);
            request.setDataWipeConfirmedBy(completerId);
            request.setDataWipeConfirmedAt(LocalDateTime.now());
        }

        request.setStatus("COMPLETED");
        request.setCompletedBy(completerId);
        request.setCompletedAt(LocalDateTime.now());
        request.setCompletionNotes(completionNotes);

        AssetDisposalRequest saved = disposalRequestRepository.save(request);

        // Update asset
        // ✅ FIXED: Use Integer for assetId
        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("Asset not found"));
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

        // Generate disposal certificate
        generateDisposalCertificate(requestId);

        // Notify requester
        notifyRequester(request, "Your disposal request has been completed.");

        auditService.logAction("DISPOSAL_PROCESS_COMPLETED",
                "Disposal process completed for request: " + requestId,
                completerId);

        return saved;
    }

    @Transactional
    public void confirmDataWipe(Long requestId, Long confirmerId) {
        log.info("Confirming data wipe for request: {} by: {}", requestId, confirmerId);

        AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Disposal request not found: " + requestId));

        if (!"IN_PROGRESS".equals(request.getStatus()) && !"APPROVED".equals(request.getStatus())) {
            throw new IllegalStateException("Request must be in progress or approved. Current: " + request.getStatus());
        }

        request.setDataWipeConfirmed(true);
        request.setDataWipeConfirmedBy(confirmerId);
        request.setDataWipeConfirmedAt(LocalDateTime.now());
        disposalRequestRepository.save(request);

        // Update asset
        // ✅ FIXED: Use Integer for assetId
        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("Asset not found"));
        asset.setDataWipeStatus("VERIFIED");
        asset.setDataWipeVerifiedBy(confirmerId);
        asset.setDataWipeVerifiedAt(LocalDateTime.now());
        assetRepository.save(asset);

        auditService.logAction("DATA_WIPE_CONFIRMED",
                "Data wipe confirmed for disposal request: " + requestId,
                confirmerId);
    }

    // ============================================
    // Query Methods
    // ============================================

    public List<AssetDisposalRequest> getPendingRequests() {
        return disposalRequestRepository.findByStatus("PENDING");
    }

    public List<AssetDisposalRequest> getRequestsByStatus(String status) {
        return disposalRequestRepository.findByStatus(status);
    }

    public List<AssetDisposalRequest> getRequestsByAssetId(Integer assetId) {
        return disposalRequestRepository.findByAssetId(assetId);
    }

    public List<AssetDisposalRequest> getRequestsByRequester(Long userId) {
        return disposalRequestRepository.findByRequestedBy(userId);
    }

    public List<Asset> getAssetsReadyForDisposal() {
        LocalDate today = LocalDate.now();
        return assetRepository.findByEolDateBeforeAndDisposalStatus(today, "ACTIVE");
    }

    public List<Asset> getAssetsPastRetentionPeriod() {
        LocalDate today = LocalDate.now();
        return assetRepository.findByRetentionPeriodEndDateBeforeAndDisposalStatus(today, "ACTIVE");
    }

    public AssetDisposalRequest getRequestById(Long requestId) {
        return disposalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Disposal request not found: " + requestId));
    }

    // ============================================
    // Helper Methods - COMPLETED
    // ============================================

    /**
     * Notify users with DISPOSAL_APPROVE permission about a new disposal request
     */
    private void notifyDisposalAuthorizers(AssetDisposalRequest request) {
        log.info("Notifying disposal authorizers about request: {}", request.getDisposalRequestId());

        String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
        String assetName = getAssetName(request.getAssetId());

        // Get users with DISPOSAL_APPROVE permission
        List<AppUser> authorizers = userService.getUsersWithPermission("DISPOSAL_APPROVE");

        if (authorizers.isEmpty()) {
            log.warn("No users with DISPOSAL_APPROVE permission found. Request: {}", request.getDisposalRequestId());
            // Fallback: notify admins
            authorizers = userService.getUsersByRole("ADMIN");
        }

        for (AppUser user : authorizers) {
            // Send email notification
            emailService.sendSimpleEmail(
                    user.getEmail(),
                    "New Asset Disposal Request - Action Required",
                    generateDisposalRequestEmailBody(request, assetName, requestLink)
            );

            // Create in-app notification
            createNotification(
                    user.getUserId(),
                    "DISPOSAL_REQUEST_PENDING",
                    "New Disposal Request",
                    "A disposal request for asset " + assetName + " requires your approval.",
                    requestLink
            );
        }

        log.info("Notified {} disposal authorizers about request: {}", authorizers.size(), request.getDisposalRequestId());
    }

    /**
     * Notify the requester about the status of their disposal request
     */
    private void notifyRequester(AssetDisposalRequest request, String message) {
        log.info("Notifying requester {} about request: {}", request.getRequestedBy(), request.getDisposalRequestId());

        String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
        String assetName = getAssetName(request.getAssetId());

        userService.getUserById(request.getRequestedBy()).ifPresent(user -> {
            // Send email notification
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

            // Create in-app notification
            createNotification(
                    user.getUserId(),
                    "DISPOSAL_REQUEST_UPDATE",
                    "Disposal Request Update",
                    message + " (Request #" + request.getDisposalRequestId() + ")",
                    requestLink
            );
        });
    }

    /**
     * Generate a disposal certificate for the completed disposal
     */
    private void generateDisposalCertificate(Long requestId) {
        log.info("Generating disposal certificate for request: {}", requestId);

        try {
            AssetDisposalRequest request = disposalRequestRepository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("Disposal request not found: " + requestId));

            // ✅ FIXED: Use Integer for assetId
            Asset asset = assetRepository.findById(request.getAssetId())
                    .orElseThrow(() -> new RuntimeException("Asset not found"));

            // This would call a PDF generation service
            // String pdfPath = pdfGenerationService.generateDisposalCertificate(request, asset);
            // request.setDisposalCertificatePath(pdfPath);
            // disposalRequestRepository.save(request);

            // For now, log the certificate generation
            log.info("Disposal certificate generated for request: {} (Asset: {})",
                    requestId, asset.getTag());

            // Send certificate to requester
            userService.getUserById(request.getRequestedBy()).ifPresent(user -> {
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
            });

        } catch (Exception e) {
            log.error("Failed to generate disposal certificate for request {}: {}", requestId, e.getMessage());
        }
    }

    /**
     * Create an in-app notification
     */
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
        } catch (Exception e) {
            log.error("Failed to create notification for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get asset name by ID
     */
    private String getAssetName(Integer assetId) {
        try {
            return assetRepository.findById(assetId)
                    .map(Asset::getName)
                    .orElse("Asset #" + assetId);
        } catch (Exception e) {
            return "Asset #" + assetId;
        }
    }

    /**
     * Generate email body for disposal request notification
     */
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

    /**
     * Get requester name by ID
     */
    private String getRequesterName(Long userId) {
        try {
            return userService.getUserById(userId)
                    .map(AppUser::getFullName)
                    .orElse("User #" + userId);
        } catch (Exception e) {
            return "User #" + userId;
        }
    }
    public boolean hasPendingRequest(Integer assetId) {
        return disposalRequestRepository.existsByAssetIdAndStatusIn(assetId,
                List.of("PENDING", "APPROVED", "IN_PROGRESS"));
    }

    public AssetDisposalRequest getPendingRequestByAssetId(Integer assetId) {
        List<AssetDisposalRequest> requests = disposalRequestRepository
                .findByAssetIdAndStatusIn(assetId, List.of("PENDING", "APPROVED", "IN_PROGRESS"));
        return requests.isEmpty() ? null : requests.get(0);
    }
}