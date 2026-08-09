package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.*;
import com.stevecodes.AssetIQPro.repository.AssetDisposalRequestRepository;
import com.stevecodes.AssetIQPro.repository.AssetRepository;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final EmailService emailService;
    private final NotificationRepository notificationRepository;
    private final BaseUrlService baseUrlService;
    private final SLAService slaService;

    private static final String POLICY_UPLOAD_DIR = "uploads/disposal/policies/";
    private static final String PROOF_UPLOAD_DIR = "uploads/disposal/proofs/";

    // ============================================
    // DISPOSAL REQUEST CREATION
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

            // ✅ START SLA TRACKING
            log.info("📊 Starting SLA tracking for disposal request: {}", saved.getDisposalRequestId());
            try {
                slaService.startSLATracking(saved.getDisposalRequestId(), "ASSET_DISPOSAL", requestedBy);
                log.info("✅ SLA tracking started successfully for disposal request: {}", saved.getDisposalRequestId());
            } catch (Exception e) {
                log.error("❌ Failed to start SLA tracking for disposal request: {}", e.getMessage(), e);
            }

            // Update asset status
            log.info("📝 Updating asset disposal status to PENDING_DISPOSAL");
            asset.setDisposalStatus("PENDING_DISPOSAL");
            assetRepository.save(asset);
            log.info("✅ Asset status updated");

            // Notify Finance approvers
            log.info("📧 Notifying Finance approvers");
            try {
                notifyFinanceApprovers(saved);
                log.info("✅ Finance approvers notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify Finance approvers: {}", e.getMessage(), e);
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

    // ============================================
    // FINANCE APPROVAL
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

            // Notify Infrastructure approvers
            try {
                notifyInfrastructureApprovers(request);
                log.info("✅ Infrastructure approvers notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify Infrastructure approvers: {}", e.getMessage(), e);
            }

            // Notify requester
            try {
                notifyRequester(request, "Your disposal request has been approved by Finance. It is now pending Infrastructure approval.");
                log.info("✅ Requester notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify requester: {}", e.getMessage(), e);
            }

            // Log audit
            auditService.logAction("DISPOSAL_FINANCE_APPROVED",
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

            // Notify requester
            try {
                notifyRequester(request, "Your disposal request has been rejected by Finance. Reason: " + reason);
                log.info("✅ Requester notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify requester: {}", e.getMessage(), e);
            }

            // Update asset status back to ACTIVE
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

            auditService.logAction("DISPOSAL_FINANCE_REJECTED",
                    "Disposal request #" + requestId + " rejected by Finance. Reason: " + reason,
                    approverId);

            log.info("=== FINANCE REJECTION END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to process Finance rejection: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // INFRASTRUCTURE APPROVAL
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

            // Upload policy file if provided
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

            // Notify Compliance approvers
            try {
                notifyComplianceApprovers(request);
                log.info("✅ Compliance approvers notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify Compliance approvers: {}", e.getMessage(), e);
            }

            // Notify requester
            try {
                notifyRequester(request, "Your disposal request has been approved by Infrastructure. It is now pending Risk & Compliance approval.");
                log.info("✅ Requester notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify requester: {}", e.getMessage(), e);
            }

            auditService.logAction("DISPOSAL_INFRA_APPROVED",
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

            // Notify requester
            try {
                notifyRequester(request, "Your disposal request has been rejected by Infrastructure. Reason: " + reason);
                log.info("✅ Requester notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify requester: {}", e.getMessage(), e);
            }

            // Update asset status back to ACTIVE
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

            auditService.logAction("DISPOSAL_INFRA_REJECTED",
                    "Disposal request #" + requestId + " rejected by Infrastructure. Reason: " + reason,
                    approverId);

            log.info("=== INFRASTRUCTURE REJECTION END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to process Infrastructure rejection: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // RISK & COMPLIANCE APPROVAL
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

            // Notify Infrastructure team (executors)
            try {
                notifyExecutionTeam(request);
                log.info("✅ Execution team notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify execution team: {}", e.getMessage(), e);
            }

            // Notify requester
            try {
                notifyRequester(request, "All approvals completed! Your disposal request is now ready for execution.");
                log.info("✅ Requester notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify requester: {}", e.getMessage(), e);
            }

            auditService.logAction("DISPOSAL_COMPLIANCE_APPROVED",
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

            // Notify requester
            try {
                notifyRequester(request, "Your disposal request has been rejected by Risk & Compliance. Reason: " + reason);
                log.info("✅ Requester notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify requester: {}", e.getMessage(), e);
            }

            // Update asset status back to ACTIVE
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

            auditService.logAction("DISPOSAL_COMPLIANCE_REJECTED",
                    "Disposal request #" + requestId + " rejected by Risk & Compliance. Reason: " + reason,
                    approverId);

            log.info("=== COMPLIANCE REJECTION END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Failed to process Compliance rejection: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // EXECUTION - FIXED: Uses DISPOSED from enum
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

            // Upload proof document
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

            // Complete SLA tracking
            try {
                slaService.completeSLATracking(requestId, "ASSET_DISPOSAL");
                log.info("✅ SLA tracking completed for disposal request: {}", requestId);
            } catch (Exception e) {
                log.error("❌ Failed to complete SLA tracking: {}", e.getMessage(), e);
            }

            disposalRequestRepository.save(request);
            log.info("✅ Disposal execution completed for request: {}", requestId);

            // ✅ FIXED: Update asset status to DISPOSED (enum value exists)
            try {
                Asset asset = assetRepository.findById(request.getAssetId())
                        .orElseThrow(() -> new RuntimeException("Asset not found"));

                // Update all asset status fields
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

            // Notify requester
            try {
                notifyRequester(request, "Your disposal request has been completed successfully!");
                log.info("✅ Requester notified");
            } catch (Exception e) {
                log.error("❌ Failed to notify requester: {}", e.getMessage(), e);
            }

            // Generate disposal certificate
            try {
                generateDisposalCertificate(requestId);
                log.info("✅ Disposal certificate generated");
            } catch (Exception e) {
                log.error("❌ Failed to generate disposal certificate: {}", e.getMessage(), e);
            }

            auditService.logAction("DISPOSAL_EXECUTED",
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
    // HELPER METHODS - FIXED UPLOAD PATHS
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

            // ✅ FIXED: Use forward slashes and correct relative path (no "admin/" prefix)
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

            // ✅ FIXED: Use forward slashes and correct relative path (no "admin/" prefix)
            return "uploads/disposal/proofs/" + filename;
        } catch (IOException e) {
            log.error("Failed to upload proof file: {}", e.getMessage());
            throw new RuntimeException("Failed to upload proof file", e);
        }
    }

    // ============================================
    // NOTIFICATION METHODS
    // ============================================

    private void notifyFinanceApprovers(AssetDisposalRequest request) {
        log.info("📧 Notifying Finance approvers about request: {}", request.getDisposalRequestId());

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
                    emailService.sendSimpleEmail(
                            user.getEmail(),
                            "Action Required: Asset Disposal Request - Finance Approval",
                            generateFinanceApprovalEmail(request, assetName, requestLink)
                    );
                    log.info("✅ Email sent to: {}", user.getEmail());

                    createNotification(
                            user.getUserId(),
                            "DISPOSAL_FINANCE_PENDING",
                            "Disposal Request - Finance Approval Required",
                            "Disposal request #" + request.getDisposalRequestId() +
                                    " for asset " + assetName + " requires your approval.",
                            requestLink
                    );
                } catch (Exception e) {
                    log.error("❌ Failed to notify user {}: {}", user.getUserId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Failed to notify Finance approvers: {}", e.getMessage(), e);
        }
    }

    private void notifyInfrastructureApprovers(AssetDisposalRequest request) {
        log.info("📧 Notifying Infrastructure approvers about request: {}", request.getDisposalRequestId());

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
                    emailService.sendSimpleEmail(
                            user.getEmail(),
                            "Action Required: Asset Disposal Request - Infrastructure Approval",
                            generateInfrastructureApprovalEmail(request, assetName, requestLink)
                    );
                    log.info("✅ Email sent to: {}", user.getEmail());

                    createNotification(
                            user.getUserId(),
                            "DISPOSAL_INFRA_PENDING",
                            "Disposal Request - Infrastructure Approval Required",
                            "Disposal request #" + request.getDisposalRequestId() +
                                    " for asset " + assetName + " requires your approval.\nPlease upload the approved disposal policy.",
                            requestLink
                    );
                } catch (Exception e) {
                    log.error("❌ Failed to notify user {}: {}", user.getUserId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Failed to notify Infrastructure approvers: {}", e.getMessage(), e);
        }
    }

    private void notifyComplianceApprovers(AssetDisposalRequest request) {
        log.info("📧 Notifying Compliance approvers about request: {}", request.getDisposalRequestId());

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
                    emailService.sendSimpleEmail(
                            user.getEmail(),
                            "Action Required: Asset Disposal Request - Risk & Compliance Approval",
                            generateComplianceApprovalEmail(request, assetName, requestLink)
                    );
                    log.info("✅ Email sent to: {}", user.getEmail());

                    createNotification(
                            user.getUserId(),
                            "DISPOSAL_COMPLIANCE_PENDING",
                            "Disposal Request - Risk & Compliance Approval Required",
                            "Disposal request #" + request.getDisposalRequestId() +
                                    " for asset " + assetName + " requires your approval.",
                            requestLink
                    );
                } catch (Exception e) {
                    log.error("❌ Failed to notify user {}: {}", user.getUserId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Failed to notify Compliance approvers: {}", e.getMessage(), e);
        }
    }

    private void notifyExecutionTeam(AssetDisposalRequest request) {
        log.info("📧 Notifying execution team about request: {}", request.getDisposalRequestId());

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
                    emailService.sendSimpleEmail(
                            user.getEmail(),
                            "Action Required: Asset Disposal Request - Ready for Execution",
                            generateExecutionEmail(request, assetName, requestLink)
                    );
                    log.info("✅ Email sent to: {}", user.getEmail());

                    createNotification(
                            user.getUserId(),
                            "DISPOSAL_READY_EXECUTE",
                            "Disposal Request - Ready for Execution",
                            "Disposal request #" + request.getDisposalRequestId() +
                                    " for asset " + assetName + " is ready for execution.\nPlease upload proof of disposal.",
                            requestLink
                    );
                } catch (Exception e) {
                    log.error("❌ Failed to notify user {}: {}", user.getUserId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Failed to notify execution team: {}", e.getMessage(), e);
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
                                            "Status: " + request.getStatusDisplay() + "\n" +
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

    // ============================================
    // EMAIL GENERATION METHODS
    // ============================================

    private String generateFinanceApprovalEmail(AssetDisposalRequest request, String assetName, String requestLink) {
        return "A new asset disposal request requires your Finance approval.\n\n" +
                "Request #: " + request.getDisposalRequestId() + "\n" +
                "Asset: " + assetName + "\n" +
                "Disposal Reason: " + request.getDisposalReason() + "\n" +
                "Disposal Method: " + request.getDisposalMethod() + "\n" +
                "Priority: " + request.getPriority() + "\n" +
                "Requested By: " + getRequesterName(request.getRequestedBy()) + "\n" +
                "Requested At: " + request.getRequestedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n\n" +
                "Please review and approve/reject:\n" + requestLink + "\n\n" +
                "Regards,\nAssetIQ-Pro System";
    }

    private String generateInfrastructureApprovalEmail(AssetDisposalRequest request, String assetName, String requestLink) {
        return "An asset disposal request requires your Infrastructure approval.\n\n" +
                "Request #: " + request.getDisposalRequestId() + "\n" +
                "Asset: " + assetName + "\n" +
                "Disposal Reason: " + request.getDisposalReason() + "\n" +
                "Disposal Method: " + request.getDisposalMethod() + "\n" +
                "Priority: " + request.getPriority() + "\n" +
                "Finance Approved By: " + getApproverName(request.getFinanceApprovedBy()) + "\n" +
                "Finance Approved At: " + formatDateTime(request.getFinanceApprovedAt()) + "\n" +
                "Finance Comment: " + (request.getFinanceComment() != null ? request.getFinanceComment() : "N/A") + "\n\n" +
                "Please upload the approved disposal policy and review:\n" + requestLink + "\n\n" +
                "Regards,\nAssetIQ-Pro System";
    }

    private String generateComplianceApprovalEmail(AssetDisposalRequest request, String assetName, String requestLink) {
        return "An asset disposal request requires your Risk & Compliance approval.\n\n" +
                "Request #: " + request.getDisposalRequestId() + "\n" +
                "Asset: " + assetName + "\n" +
                "Disposal Reason: " + request.getDisposalReason() + "\n" +
                "Disposal Method: " + request.getDisposalMethod() + "\n" +
                "Priority: " + request.getPriority() + "\n" +
                "Finance Approved By: " + getApproverName(request.getFinanceApprovedBy()) + "\n" +
                "Infrastructure Approved By: " + getApproverName(request.getInfraApprovedBy()) + "\n" +
                "Infrastructure Comment: " + (request.getInfraComment() != null ? request.getInfraComment() : "N/A") + "\n\n" +
                "Please review and approve/reject:\n" + requestLink + "\n\n" +
                "Regards,\nAssetIQ-Pro System";
    }

    private String generateExecutionEmail(AssetDisposalRequest request, String assetName, String requestLink) {
        return "An asset disposal request is ready for execution.\n\n" +
                "Request #: " + request.getDisposalRequestId() + "\n" +
                "Asset: " + assetName + "\n" +
                "Disposal Method: " + request.getDisposalMethod() + "\n" +
                "All approvals have been completed:\n" +
                "✅ Finance: " + getApproverName(request.getFinanceApprovedBy()) + "\n" +
                "✅ Infrastructure: " + getApproverName(request.getInfraApprovedBy()) + "\n" +
                "✅ Risk & Compliance: " + getApproverName(request.getComplianceApprovedBy()) + "\n\n" +
                "Please execute the disposal and upload proof:\n" + requestLink + "\n\n" +
                "Regards,\nAssetIQ-Pro System";
    }

    // ============================================
    // UTILITY METHODS
    // ============================================

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

    private void generateDisposalCertificate(Long requestId) {
        log.info("📄 Generating disposal certificate for request: {}", requestId);
        // TODO: Implement PDF generation for disposal certificate
        // This can be added later when PDF generation service is ready
    }
}