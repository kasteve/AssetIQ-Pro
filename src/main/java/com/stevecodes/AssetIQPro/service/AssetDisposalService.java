package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.*;
import com.stevecodes.AssetIQPro.repository.AssetDisposalRequestRepository;
import com.stevecodes.AssetIQPro.repository.AssetRepository;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final EmailAsyncService emailAsyncService;
    private final NotificationRepository notificationRepository;
    private final BaseUrlService baseUrlService;
    private final SLAService slaService;

    private static final String POLICY_UPLOAD_DIR = "uploads/disposal/policies/";
    private static final String PROOF_UPLOAD_DIR = "uploads/disposal/proofs/";

    // ============================================
    // DISPOSAL REQUEST CREATION - ASYNC NOTIFICATIONS
    // ============================================

    @Transactional
    public AssetDisposalRequest createDisposalRequest(Integer assetId, Long requestedBy,
                                                      String disposalReason, String disposalMethod,
                                                      String priority) {
        log.info("=== CREATE DISPOSAL REQUEST START ===");
        log.info("📝 Creating disposal request for asset: {} by user: {}", assetId, requestedBy);

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
                    List.of("PENDING", "APPROVED", "IN_PROGRESS", "PENDING_FINANCE_APPROVAL",
                            "PENDING_INFRA_APPROVAL", "PENDING_COMPLIANCE_APPROVAL", "PENDING_EXECUTION"))) {
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

            // Set initial workflow status - goes to Finance first
            request.setStatus("PENDING_FINANCE_APPROVAL");
            request.setCurrentApprovalStep("FINANCE");
            request.setApprovalFlowStatus("IN_PROGRESS");

            // Initialize approval statuses
            request.setFinanceStatus("PENDING");
            request.setInfraStatus("PENDING");
            request.setComplianceStatus("PENDING");

            request.setRequestedAt(LocalDateTime.now());

            log.info("💾 Saving disposal request to database");
            AssetDisposalRequest saved = disposalRequestRepository.save(request);
            log.info("✅ Disposal request saved with ID: {}", saved.getDisposalRequestId());

            // ✅ START SLA TRACKING (Synchronous - quick)
            log.info("📊 Starting SLA tracking for disposal request: {}", saved.getDisposalRequestId());
            try {
                slaService.startSLATracking(saved.getDisposalRequestId(), "ASSET_DISPOSAL", requestedBy);
                log.info("✅ SLA tracking started successfully for disposal request: {}", saved.getDisposalRequestId());
            } catch (Exception e) {
                log.error("❌ Failed to start SLA tracking for disposal request: {}", e.getMessage(), e);
            }

            // Update asset status (Synchronous - quick)
            log.info("📝 Updating asset disposal status to PENDING_DISPOSAL");
            asset.setDisposalStatus("PENDING_DISPOSAL");
            assetRepository.save(asset);
            log.info("✅ Asset status updated");

            // ✅ ASYNC: Notify Finance approvers (non-blocking)
            log.info("📧 Queueing Finance approver notifications asynchronously");
            notifyFinanceApproversAsync(saved);

            // ✅ ASYNC: Log audit (non-blocking)
            logAuditAsync("DISPOSAL_REQUEST_CREATED",
                    "Disposal request created for asset: " + asset.getTag(),
                    requestedBy);

            log.info("=== CREATE DISPOSAL REQUEST END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Failed to create disposal request: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // FINANCE APPROVAL - ASYNC NOTIFICATIONS
    // ============================================

    @Transactional
    public void approveByFinance(Long requestId, Long approverId, String comment) {
        log.info("=== FINANCE APPROVAL START ===");
        log.info("📝 Finance approver {} approving request: {}", approverId, requestId);

        try {
            AssetDisposalRequest request = validateRequest(requestId);

            // Validate current step
            if (!"FINANCE".equals(request.getCurrentApprovalStep())) {
                throw new IllegalStateException("Request is not at Finance approval step. Current: " + request.getCurrentApprovalStep());
            }

            if (!"PENDING".equals(request.getFinanceStatus())) {
                throw new IllegalStateException("Finance approval already processed. Status: " + request.getFinanceStatus());
            }

            request.setFinanceApprovedBy(approverId);
            request.setFinanceApprovedAt(LocalDateTime.now());
            request.setFinanceComment(comment);
            request.setFinanceStatus("APPROVED");

            // Move to next step
            request.setCurrentApprovalStep("INFRASTRUCTURE");
            request.setStatus("PENDING_INFRA_APPROVAL");

            disposalRequestRepository.save(request);
            log.info("✅ Finance approval completed for request: {}", requestId);

            // ✅ ASYNC: Notify Infrastructure approvers (non-blocking)
            log.info("📧 Queueing Infrastructure approver notifications asynchronously");
            notifyInfrastructureApproversAsync(request);

            // ✅ ASYNC: Notify requester (non-blocking)
            log.info("📧 Queueing requester notification asynchronously");
            notifyRequesterAsync(request, "Your disposal request has been approved by Finance. It is now pending Infrastructure approval.");

            // ✅ ASYNC: Log audit (non-blocking)
            logAuditAsync("DISPOSAL_FINANCE_APPROVED",
                    "Disposal request #" + requestId + " approved by Finance",
                    approverId);

            log.info("=== FINANCE APPROVAL END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to process Finance approval: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void rejectByFinance(Long requestId, Long approverId, String reason) {
        log.info("=== FINANCE REJECTION START ===");
        log.info("📝 Finance approver {} rejecting request: {}", approverId, requestId);

        try {
            AssetDisposalRequest request = validateRequest(requestId);

            if (!"FINANCE".equals(request.getCurrentApprovalStep())) {
                throw new IllegalStateException("Request is not at Finance approval step.");
            }

            request.setFinanceApprovedBy(approverId);
            request.setFinanceApprovedAt(LocalDateTime.now());
            request.setFinanceComment(reason);
            request.setFinanceStatus("REJECTED");
            request.setStatus("REJECTED");
            request.setRejectionReason("Finance Rejection: " + reason);
            request.setApprovalFlowStatus("REJECTED");

            disposalRequestRepository.save(request);
            log.info("✅ Finance rejection processed for request: {}", requestId);

            // ✅ ASYNC: Notify requester (non-blocking)
            log.info("📧 Queueing requester notification asynchronously");
            notifyRequesterAsync(request, "Your disposal request has been rejected by Finance. Reason: " + reason);

            // Update asset status back to ACTIVE (Synchronous - quick)
            try {
                Asset asset = assetRepository.findById(request.getAssetId()).orElse(null);
                if (asset != null) {
                    asset.setDisposalStatus("ACTIVE");
                    assetRepository.save(asset);
                    log.info("✅ Asset status reverted to ACTIVE");
                }
            } catch (Exception e) {
                log.error("❌ Failed to update asset status: {}", e.getMessage(), e);
            }

            // ✅ ASYNC: Log audit
            logAuditAsync("DISPOSAL_FINANCE_REJECTED",
                    "Disposal request #" + requestId + " rejected by Finance. Reason: " + reason,
                    approverId);

            log.info("=== FINANCE REJECTION END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to process Finance rejection: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // INFRASTRUCTURE APPROVAL - ASYNC NOTIFICATIONS
    // ============================================

    @Transactional
    public void approveByInfrastructure(Long requestId, Long approverId, String comment, MultipartFile policyFile) {
        log.info("=== INFRASTRUCTURE APPROVAL START ===");
        log.info("📝 Infrastructure approver {} approving request: {}", approverId, requestId);

        try {
            AssetDisposalRequest request = validateRequest(requestId);

            if (!"INFRASTRUCTURE".equals(request.getCurrentApprovalStep())) {
                throw new IllegalStateException("Request is not at Infrastructure approval step.");
            }

            // Upload policy file if provided (Synchronous - quick file operation)
            String policyPath = null;
            if (policyFile != null && !policyFile.isEmpty()) {
                policyPath = uploadPolicyFile(policyFile, requestId);
                request.setDisposalPolicyPath(policyPath);
                log.info("✅ Policy file uploaded: {}", policyPath);
            }

            request.setInfraApprovedBy(approverId);
            request.setInfraApprovedAt(LocalDateTime.now());
            request.setInfraComment(comment);
            request.setInfraStatus("APPROVED");

            // Move to next step
            request.setCurrentApprovalStep("COMPLIANCE");
            request.setStatus("PENDING_COMPLIANCE_APPROVAL");

            disposalRequestRepository.save(request);
            log.info("✅ Infrastructure approval completed for request: {}", requestId);

            // ✅ ASYNC: Notify Compliance approvers
            log.info("📧 Queueing Compliance approver notifications asynchronously");
            notifyComplianceApproversAsync(request);

            // ✅ ASYNC: Notify requester
            log.info("📧 Queueing requester notification asynchronously");
            notifyRequesterAsync(request, "Your disposal request has been approved by Infrastructure. It is now pending Risk & Compliance approval.");

            // ✅ ASYNC: Log audit
            logAuditAsync("DISPOSAL_INFRA_APPROVED",
                    "Disposal request #" + requestId + " approved by Infrastructure" +
                            (policyPath != null ? ". Policy uploaded: " + policyPath : ""),
                    approverId);

            log.info("=== INFRASTRUCTURE APPROVAL END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to process Infrastructure approval: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void rejectByInfrastructure(Long requestId, Long approverId, String reason) {
        log.info("=== INFRASTRUCTURE REJECTION START ===");
        log.info("📝 Infrastructure approver {} rejecting request: {}", approverId, requestId);

        try {
            AssetDisposalRequest request = validateRequest(requestId);

            if (!"INFRASTRUCTURE".equals(request.getCurrentApprovalStep())) {
                throw new IllegalStateException("Request is not at Infrastructure approval step.");
            }

            request.setInfraApprovedBy(approverId);
            request.setInfraApprovedAt(LocalDateTime.now());
            request.setInfraComment(reason);
            request.setInfraStatus("REJECTED");
            request.setStatus("REJECTED");
            request.setRejectionReason("Infrastructure Rejection: " + reason);
            request.setApprovalFlowStatus("REJECTED");

            disposalRequestRepository.save(request);
            log.info("✅ Infrastructure rejection processed for request: {}", requestId);

            // ✅ ASYNC: Notify requester
            log.info("📧 Queueing requester notification asynchronously");
            notifyRequesterAsync(request, "Your disposal request has been rejected by Infrastructure. Reason: " + reason);

            // Update asset status back to ACTIVE (Synchronous - quick)
            try {
                Asset asset = assetRepository.findById(request.getAssetId()).orElse(null);
                if (asset != null) {
                    asset.setDisposalStatus("ACTIVE");
                    assetRepository.save(asset);
                    log.info("✅ Asset status reverted to ACTIVE");
                }
            } catch (Exception e) {
                log.error("❌ Failed to update asset status: {}", e.getMessage(), e);
            }

            // ✅ ASYNC: Log audit
            logAuditAsync("DISPOSAL_INFRA_REJECTED",
                    "Disposal request #" + requestId + " rejected by Infrastructure. Reason: " + reason,
                    approverId);

            log.info("=== INFRASTRUCTURE REJECTION END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to process Infrastructure rejection: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // COMPLIANCE APPROVAL - ASYNC NOTIFICATIONS
    // ============================================

    @Transactional
    public void approveByCompliance(Long requestId, Long approverId, String comment) {
        log.info("=== COMPLIANCE APPROVAL START ===");
        log.info("📝 Compliance approver {} approving request: {}", approverId, requestId);

        try {
            AssetDisposalRequest request = validateRequest(requestId);

            if (!"COMPLIANCE".equals(request.getCurrentApprovalStep())) {
                throw new IllegalStateException("Request is not at Compliance approval step.");
            }

            request.setComplianceApprovedBy(approverId);
            request.setComplianceApprovedAt(LocalDateTime.now());
            request.setComplianceComment(comment);
            request.setComplianceStatus("APPROVED");

            // All approvals done - move to execution
            request.setCurrentApprovalStep("EXECUTION");
            request.setStatus("PENDING_EXECUTION");

            disposalRequestRepository.save(request);
            log.info("✅ Compliance approval completed for request: {}", requestId);

            // ✅ ASYNC: Notify Execution team
            log.info("📧 Queueing Execution team notifications asynchronously");
            notifyExecutionTeamAsync(request);

            // ✅ ASYNC: Notify requester
            log.info("📧 Queueing requester notification asynchronously");
            notifyRequesterAsync(request, "All approvals completed! Your disposal request is now ready for execution.");

            // ✅ ASYNC: Log audit
            logAuditAsync("DISPOSAL_COMPLIANCE_APPROVED",
                    "Disposal request #" + requestId + " approved by Risk & Compliance",
                    approverId);

            log.info("=== COMPLIANCE APPROVAL END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to process Compliance approval: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void rejectByCompliance(Long requestId, Long approverId, String reason) {
        log.info("=== COMPLIANCE REJECTION START ===");
        log.info("📝 Compliance approver {} rejecting request: {}", approverId, requestId);

        try {
            AssetDisposalRequest request = validateRequest(requestId);

            if (!"COMPLIANCE".equals(request.getCurrentApprovalStep())) {
                throw new IllegalStateException("Request is not at Compliance approval step.");
            }

            request.setComplianceApprovedBy(approverId);
            request.setComplianceApprovedAt(LocalDateTime.now());
            request.setComplianceComment(reason);
            request.setComplianceStatus("REJECTED");
            request.setStatus("REJECTED");
            request.setRejectionReason("Compliance Rejection: " + reason);
            request.setApprovalFlowStatus("REJECTED");

            disposalRequestRepository.save(request);
            log.info("✅ Compliance rejection processed for request: {}", requestId);

            // ✅ ASYNC: Notify requester
            log.info("📧 Queueing requester notification asynchronously");
            notifyRequesterAsync(request, "Your disposal request has been rejected by Risk & Compliance. Reason: " + reason);

            // Update asset status back to ACTIVE (Synchronous - quick)
            try {
                Asset asset = assetRepository.findById(request.getAssetId()).orElse(null);
                if (asset != null) {
                    asset.setDisposalStatus("ACTIVE");
                    assetRepository.save(asset);
                    log.info("✅ Asset status reverted to ACTIVE");
                }
            } catch (Exception e) {
                log.error("❌ Failed to update asset status: {}", e.getMessage(), e);
            }

            // ✅ ASYNC: Log audit
            logAuditAsync("DISPOSAL_COMPLIANCE_REJECTED",
                    "Disposal request #" + requestId + " rejected by Risk & Compliance. Reason: " + reason,
                    approverId);

            log.info("=== COMPLIANCE REJECTION END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to process Compliance rejection: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // EXECUTION - ASYNC NOTIFICATIONS
    // ============================================

    @Transactional
    public void executeDisposal(Long requestId, Long executorId, String notes, MultipartFile proofDocument) {
        log.info("=== EXECUTION START ===");
        log.info("📝 Executor {} executing disposal request: {}", executorId, requestId);

        try {
            AssetDisposalRequest request = validateRequest(requestId);

            if (!"EXECUTION".equals(request.getCurrentApprovalStep())) {
                throw new IllegalStateException("Request is not ready for execution.");
            }

            // Upload proof document (Synchronous - quick file operation)
            String proofPath = null;
            if (proofDocument != null && !proofDocument.isEmpty()) {
                proofPath = uploadProofFile(proofDocument, requestId);
                request.setProofDocumentPath(proofPath);
                log.info("✅ Proof document uploaded: {}", proofPath);
            }

            request.setExecutedBy(executorId);
            request.setExecutedAt(LocalDateTime.now());
            request.setStatus("COMPLETED");
            request.setCurrentApprovalStep("COMPLETED");
            request.setApprovalFlowStatus("COMPLETED");
            request.setCompletionNotes(notes);

            // Complete SLA tracking (Synchronous - quick)
            try {
                slaService.completeSLATracking(requestId, "ASSET_DISPOSAL");
                log.info("✅ SLA tracking completed for disposal request: {}", requestId);
            } catch (Exception e) {
                log.error("❌ Failed to complete SLA tracking: {}", e.getMessage(), e);
            }

            disposalRequestRepository.save(request);
            log.info("✅ Disposal execution completed for request: {}", requestId);

            // Update asset status to DISPOSED (Synchronous - quick)
            try {
                Asset asset = assetRepository.findById(request.getAssetId())
                        .orElseThrow(() -> new RuntimeException("Asset not found"));

                asset.setStatus(Asset.AssetStatus.DISPOSED);
                asset.setDisposalStatus("DISPOSED");
                asset.setAssetLifecycleStatus("DISPOSED");
                asset.setDisposalDate(LocalDate.now());
                asset.setDisposalMethod(request.getDisposalMethod());
                asset.setDisposalCompletedBy(executorId);
                asset.setDisposalCompletedAt(LocalDateTime.now());
                asset.setDisposalNotes(notes);

                if (proofPath != null) {
                    asset.setDisposalCertificatePath(proofPath);
                }

                assetRepository.save(asset);
                log.info("✅ Asset {} marked as DISPOSED", asset.getTag());
            } catch (Exception e) {
                log.error("❌ Failed to update asset status: {}", e.getMessage(), e);
            }

            // ✅ ASYNC: Notify requester
            log.info("📧 Queueing requester notification asynchronously");
            notifyRequesterAsync(request, "Your disposal request has been completed successfully!");

            // ✅ ASYNC: Generate disposal certificate (non-blocking)
            log.info("📄 Queueing disposal certificate generation asynchronously");
            generateDisposalCertificateAsync(requestId);

            // ✅ ASYNC: Log audit
            logAuditAsync("DISPOSAL_EXECUTED",
                    "Disposal request #" + requestId + " executed by " + executorId +
                            (proofPath != null ? ". Proof uploaded: " + proofPath : ""),
                    executorId);

            log.info("=== EXECUTION END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to execute disposal: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // ASYNC NOTIFICATION METHODS
    // ============================================

    @Async("emailTaskExecutor")
    protected void notifyFinanceApproversAsync(AssetDisposalRequest request) {
        log.info("📧 Async: Notifying Finance approvers about request: {}", request.getDisposalRequestId());

        try {
            String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
            String assetName = getAssetName(request.getAssetId());

            List<AppUser> approvers = userService.getUsersWithPermission("DISPOSAL_FINANCE_APPROVE");

            if (approvers.isEmpty()) {
                log.warn("⚠️ No users with DISPOSAL_FINANCE_APPROVE permission found.");
                approvers = userService.getUsersByRole("FINANCE");
            }

            for (AppUser user : approvers) {
                try {
                    emailAsyncService.sendHtmlEmailAsync(
                            user.getEmail(),
                            "Action Required: Asset Disposal Request - Finance Approval",
                            generateFinanceApprovalEmailHTML(request, assetName, requestLink)
                    );
                    log.debug("✅ Async email queued for: {}", user.getEmail());

                    createNotificationAsync(
                            user.getUserId(),
                            "DISPOSAL_FINANCE_PENDING",
                            "Disposal Request - Finance Approval Required",
                            "Disposal request #" + request.getDisposalRequestId() +
                                    " for asset " + assetName + " requires your approval.",
                            requestLink
                    );
                } catch (Exception e) {
                    log.error("❌ Failed to queue notification for user {}: {}", user.getUserId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Failed to queue Finance approver notifications: {}", e.getMessage(), e);
        }
    }

    @Async("emailTaskExecutor")
    protected void notifyInfrastructureApproversAsync(AssetDisposalRequest request) {
        log.info("📧 Async: Notifying Infrastructure approvers about request: {}", request.getDisposalRequestId());

        try {
            String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
            String assetName = getAssetName(request.getAssetId());

            List<AppUser> approvers = userService.getUsersWithPermission("DISPOSAL_INFRA_APPROVE");

            if (approvers.isEmpty()) {
                log.warn("⚠️ No users with DISPOSAL_INFRA_APPROVE permission found.");
                approvers = userService.getUsersByRole("INFRA");
            }

            for (AppUser user : approvers) {
                try {
                    emailAsyncService.sendHtmlEmailAsync(
                            user.getEmail(),
                            "Action Required: Asset Disposal Request - Infrastructure Approval",
                            generateInfrastructureApprovalEmailHTML(request, assetName, requestLink)
                    );
                    log.debug("✅ Async email queued for: {}", user.getEmail());

                    createNotificationAsync(
                            user.getUserId(),
                            "DISPOSAL_INFRA_PENDING",
                            "Disposal Request - Infrastructure Approval Required",
                            "Disposal request #" + request.getDisposalRequestId() +
                                    " for asset " + assetName + " requires your approval.\nPlease upload the approved disposal policy.",
                            requestLink
                    );
                } catch (Exception e) {
                    log.error("❌ Failed to queue notification for user {}: {}", user.getUserId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Failed to queue Infrastructure approver notifications: {}", e.getMessage(), e);
        }
    }

    @Async("emailTaskExecutor")
    protected void notifyComplianceApproversAsync(AssetDisposalRequest request) {
        log.info("📧 Async: Notifying Compliance approvers about request: {}", request.getDisposalRequestId());

        try {
            String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
            String assetName = getAssetName(request.getAssetId());

            List<AppUser> approvers = userService.getUsersWithPermission("DISPOSAL_COMPLIANCE_APPROVE");

            if (approvers.isEmpty()) {
                log.warn("⚠️ No users with DISPOSAL_COMPLIANCE_APPROVE permission found.");
                approvers = userService.getUsersByRole("COMPLIANCE");
            }

            for (AppUser user : approvers) {
                try {
                    emailAsyncService.sendHtmlEmailAsync(
                            user.getEmail(),
                            "Action Required: Asset Disposal Request - Risk & Compliance Approval",
                            generateComplianceApprovalEmailHTML(request, assetName, requestLink)
                    );
                    log.debug("✅ Async email queued for: {}", user.getEmail());

                    createNotificationAsync(
                            user.getUserId(),
                            "DISPOSAL_COMPLIANCE_PENDING",
                            "Disposal Request - Risk & Compliance Approval Required",
                            "Disposal request #" + request.getDisposalRequestId() +
                                    " for asset " + assetName + " requires your approval.",
                            requestLink
                    );
                } catch (Exception e) {
                    log.error("❌ Failed to queue notification for user {}: {}", user.getUserId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Failed to queue Compliance approver notifications: {}", e.getMessage(), e);
        }
    }

    @Async("emailTaskExecutor")
    protected void notifyExecutionTeamAsync(AssetDisposalRequest request) {
        log.info("📧 Async: Notifying execution team about request: {}", request.getDisposalRequestId());

        try {
            String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
            String assetName = getAssetName(request.getAssetId());

            List<AppUser> executors = userService.getUsersWithPermission("DISPOSAL_EXECUTE");

            if (executors.isEmpty()) {
                log.warn("⚠️ No users with DISPOSAL_EXECUTE permission found.");
                executors = userService.getUsersByRole("INFRA");
            }

            for (AppUser user : executors) {
                try {
                    emailAsyncService.sendHtmlEmailAsync(
                            user.getEmail(),
                            "Action Required: Asset Disposal Request - Ready for Execution",
                            generateExecutionEmailHTML(request, assetName, requestLink)
                    );
                    log.debug("✅ Async email queued for: {}", user.getEmail());

                    createNotificationAsync(
                            user.getUserId(),
                            "DISPOSAL_READY_EXECUTE",
                            "Disposal Request - Ready for Execution",
                            "Disposal request #" + request.getDisposalRequestId() +
                                    " for asset " + assetName + " is ready for execution.\nPlease upload proof of disposal.",
                            requestLink
                    );
                } catch (Exception e) {
                    log.error("❌ Failed to queue notification for user {}: {}", user.getUserId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Failed to queue execution team notifications: {}", e.getMessage(), e);
        }
    }

    @Async("emailTaskExecutor")
    protected void notifyRequesterAsync(AssetDisposalRequest request, String message) {
        log.info("📧 Async: Notifying requester {} about request: {}", request.getRequestedBy(), request.getDisposalRequestId());

        try {
            String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
            String assetName = getAssetName(request.getAssetId());

            userService.getUserById(request.getRequestedBy()).ifPresentOrElse(
                    user -> {
                        try {
                            emailAsyncService.sendHtmlEmailAsync(
                                    user.getEmail(),
                                    "Disposal Request Update - #" + request.getDisposalRequestId(),
                                    generateRequesterUpdateEmailHTML(user, request, assetName, message, requestLink)
                            );
                            log.debug("✅ Async email queued for requester: {}", user.getEmail());

                            createNotificationAsync(
                                    user.getUserId(),
                                    "DISPOSAL_REQUEST_UPDATE",
                                    "Disposal Request Update",
                                    message + " (Request #" + request.getDisposalRequestId() + ")",
                                    requestLink
                            );
                        } catch (Exception e) {
                            log.error("❌ Failed to queue notification for requester: {}", e.getMessage(), e);
                        }
                    },
                    () -> log.warn("⚠️ Requester not found with ID: {}", request.getRequestedBy())
            );

        } catch (Exception e) {
            log.error("❌ Failed to queue requester notification: {}", e.getMessage(), e);
        }
    }

    @Async("auditTaskExecutor")
    protected void logAuditAsync(String action, String details, Long userId) {
        try {
            auditService.logAction(action, details, userId);
            log.debug("✅ Async audit logged: {}", action);
        } catch (Exception e) {
            log.error("❌ Failed to log audit asynchronously: {}", e.getMessage(), e);
        }
    }

    @Async("notificationTaskExecutor")
    protected void createNotificationAsync(Long userId, String type, String title, String message, String link) {
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
            log.debug("✅ Async notification created for user: {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to create notification for user {}: {}", userId, e.getMessage(), e);
        }
    }

    @Async("pdfTaskExecutor")
    protected void generateDisposalCertificateAsync(Long requestId) {
        log.info("📄 Async: Generating disposal certificate for request: {}", requestId);
        // TODO: Implement PDF generation for disposal certificate
        try {
            // Simulate PDF generation work
            Thread.sleep(200);
            log.info("✅ Disposal certificate generated for request: {}", requestId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Certificate generation interrupted for request: {}", requestId);
        }
    }

    // ============================================
    // EMAIL HTML GENERATORS (for async emails)
    // ============================================

    private String generateFinanceApprovalEmailHTML(AssetDisposalRequest request, String assetName, String requestLink) {
        return "<html><body style='font-family: Arial, sans-serif;'>" +
                "<h2 style='color: #0d6efd;'>Asset Disposal Request - Finance Approval</h2>" +
                "<p>A new asset disposal request requires your Finance approval.</p>" +
                "<table style='border-collapse: collapse; width: 100%;'>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Request #</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + request.getDisposalRequestId() + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Asset</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + assetName + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Disposal Reason</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + request.getDisposalReason() + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Disposal Method</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + request.getDisposalMethod() + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Priority</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + request.getPriority() + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Requested By</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + getRequesterName(request.getRequestedBy()) + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Requested At</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + request.getRequestedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "</td></tr>" +
                "</table>" +
                "<br>" +
                "<a href='" + requestLink + "' style='background: #0d6efd; color: white; padding: 10px 20px; text-decoration: none; border-radius: 8px;'>Review & Approve</a>" +
                "<br><br>" +
                "<p style='color: #6c757d; font-size: 12px;'>This is an automated message from AssetIQ-Pro.</p>" +
                "</body></html>";
    }

    private String generateInfrastructureApprovalEmailHTML(AssetDisposalRequest request, String assetName, String requestLink) {
        return "<html><body style='font-family: Arial, sans-serif;'>" +
                "<h2 style='color: #fd7e14;'>Asset Disposal Request - Infrastructure Approval</h2>" +
                "<p>An asset disposal request requires your Infrastructure approval.</p>" +
                "<table style='border-collapse: collapse; width: 100%;'>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Request #</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + request.getDisposalRequestId() + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Asset</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + assetName + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Finance Approved By</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + getApproverName(request.getFinanceApprovedBy()) + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Finance Comment</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + (request.getFinanceComment() != null ? request.getFinanceComment() : "N/A") + "</td></tr>" +
                "</table>" +
                "<br>" +
                "<p><strong>Please upload the approved disposal policy and review:</strong></p>" +
                "<a href='" + requestLink + "' style='background: #fd7e14; color: white; padding: 10px 20px; text-decoration: none; border-radius: 8px;'>Review & Approve</a>" +
                "<br><br>" +
                "<p style='color: #6c757d; font-size: 12px;'>This is an automated message from AssetIQ-Pro.</p>" +
                "</body></html>";
    }

    private String generateComplianceApprovalEmailHTML(AssetDisposalRequest request, String assetName, String requestLink) {
        return "<html><body style='font-family: Arial, sans-serif;'>" +
                "<h2 style='color: #198754;'>Asset Disposal Request - Risk & Compliance Approval</h2>" +
                "<p>An asset disposal request requires your Risk & Compliance approval.</p>" +
                "<table style='border-collapse: collapse; width: 100%;'>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Request #</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + request.getDisposalRequestId() + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Asset</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + assetName + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Finance Approved By</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + getApproverName(request.getFinanceApprovedBy()) + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Infrastructure Approved By</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + getApproverName(request.getInfraApprovedBy()) + "</td></tr>" +
                "</table>" +
                "<br>" +
                "<a href='" + requestLink + "' style='background: #198754; color: white; padding: 10px 20px; text-decoration: none; border-radius: 8px;'>Review & Approve</a>" +
                "<br><br>" +
                "<p style='color: #6c757d; font-size: 12px;'>This is an automated message from AssetIQ-Pro.</p>" +
                "</body></html>";
    }

    private String generateExecutionEmailHTML(AssetDisposalRequest request, String assetName, String requestLink) {
        return "<html><body style='font-family: Arial, sans-serif;'>" +
                "<h2 style='color: #0d6efd;'>Asset Disposal Request - Ready for Execution</h2>" +
                "<p>An asset disposal request is ready for execution.</p>" +
                "<table style='border-collapse: collapse; width: 100%;'>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Request #</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + request.getDisposalRequestId() + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Asset</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + assetName + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Disposal Method</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + request.getDisposalMethod() + "</td></tr>" +
                "</table>" +
                "<br>" +
                "<p><strong>All approvals have been completed:</strong></p>" +
                "<ul>" +
                "<li>✅ Finance: " + getApproverName(request.getFinanceApprovedBy()) + "</li>" +
                "<li>✅ Infrastructure: " + getApproverName(request.getInfraApprovedBy()) + "</li>" +
                "<li>✅ Risk & Compliance: " + getApproverName(request.getComplianceApprovedBy()) + "</li>" +
                "</ul>" +
                "<br>" +
                "<a href='" + requestLink + "' style='background: #0d6efd; color: white; padding: 10px 20px; text-decoration: none; border-radius: 8px;'>Execute Disposal</a>" +
                "<br><br>" +
                "<p style='color: #6c757d; font-size: 12px;'>This is an automated message from AssetIQ-Pro.</p>" +
                "</body></html>";
    }

    private String generateRequesterUpdateEmailHTML(AppUser user, AssetDisposalRequest request,
                                                    String assetName, String message, String requestLink) {
        return "<html><body style='font-family: Arial, sans-serif;'>" +
                "<h2 style='color: #0d6efd;'>Disposal Request Update</h2>" +
                "<p>Dear " + user.getFullName() + ",</p>" +
                "<p>Your disposal request for asset <strong>" + assetName + "</strong> has been updated.</p>" +
                "<table style='border-collapse: collapse; width: 100%;'>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Request #</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + request.getDisposalRequestId() + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Status</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + request.getStatusDisplay() + "</td></tr>" +
                "<tr><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'><strong>Message</strong></td><td style='padding: 8px; border-bottom: 1px solid #e9ecef;'>" + message + "</td></tr>" +
                "</table>" +
                "<br>" +
                "<a href='" + requestLink + "' style='background: #0d6efd; color: white; padding: 10px 20px; text-decoration: none; border-radius: 8px;'>View Details</a>" +
                "<br><br>" +
                "<p style='color: #6c757d; font-size: 12px;'>This is an automated message from AssetIQ-Pro.</p>" +
                "</body></html>";
    }

    // ============================================
    // QUERY METHODS
    // ============================================

    public List<AssetDisposalRequest> getPendingFinanceRequests() {
        return disposalRequestRepository.findByStatus("PENDING_FINANCE_APPROVAL");
    }

    public List<AssetDisposalRequest> getPendingInfraRequests() {
        return disposalRequestRepository.findByStatus("PENDING_INFRA_APPROVAL");
    }

    public List<AssetDisposalRequest> getPendingComplianceRequests() {
        return disposalRequestRepository.findByStatus("PENDING_COMPLIANCE_APPROVAL");
    }

    public List<AssetDisposalRequest> getPendingExecutionRequests() {
        return disposalRequestRepository.findByStatus("PENDING_EXECUTION");
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

    public boolean hasPendingRequest(Integer assetId) {
        return disposalRequestRepository.existsByAssetIdAndStatusIn(assetId,
                List.of("PENDING", "APPROVED", "IN_PROGRESS", "PENDING_FINANCE_APPROVAL",
                        "PENDING_INFRA_APPROVAL", "PENDING_COMPLIANCE_APPROVAL", "PENDING_EXECUTION"));
    }

    public AssetDisposalRequest getPendingRequestByAssetId(Integer assetId) {
        List<AssetDisposalRequest> requests = disposalRequestRepository
                .findByAssetIdAndStatusIn(assetId, List.of("PENDING_FINANCE_APPROVAL", "PENDING_INFRA_APPROVAL",
                        "PENDING_COMPLIANCE_APPROVAL", "PENDING_EXECUTION", "IN_PROGRESS"));
        return requests.isEmpty() ? null : requests.get(0);
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private AssetDisposalRequest validateRequest(Long requestId) {
        return disposalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Disposal request not found: " + requestId));
    }

    private String uploadPolicyFile(MultipartFile file, Long requestId) {
        try {
            Path uploadPath = Paths.get(POLICY_UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = "policy_" + requestId + "_" + System.currentTimeMillis() + "_" +
                    file.getOriginalFilename().replaceAll("\\s+", "_");
            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, file.getBytes());

            return "uploads/disposal/policies/" + filename;
        } catch (IOException e) {
            log.error("Failed to upload policy file: {}", e.getMessage());
            throw new RuntimeException("Failed to upload policy file", e);
        }
    }

    private String uploadProofFile(MultipartFile file, Long requestId) {
        try {
            Path uploadPath = Paths.get(PROOF_UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = "proof_" + requestId + "_" + System.currentTimeMillis() + "_" +
                    file.getOriginalFilename().replaceAll("\\s+", "_");
            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, file.getBytes());

            return "uploads/disposal/proofs/" + filename;
        } catch (IOException e) {
            log.error("Failed to upload proof file: {}", e.getMessage());
            throw new RuntimeException("Failed to upload proof file", e);
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

    private String getApproverName(Long userId) {
        if (userId == null) return "N/A";
        try {
            return userService.getUserById(userId)
                    .map(AppUser::getFullName)
                    .orElse("User #" + userId);
        } catch (Exception e) {
            return "User #" + userId;
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }
}