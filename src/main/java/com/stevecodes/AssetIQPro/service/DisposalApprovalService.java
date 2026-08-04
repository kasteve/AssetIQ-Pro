package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.*;
import com.stevecodes.AssetIQPro.repository.DisposalApprovalLevelRepository;
import com.stevecodes.AssetIQPro.repository.DisposalApprovalRepository;
import com.stevecodes.AssetIQPro.repository.AssetRepository;
import com.stevecodes.AssetIQPro.repository.AssetDisposalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisposalApprovalService {

    private final DisposalApprovalRepository approvalRepository;
    private final DisposalApprovalLevelRepository approvalLevelRepository;
    private final AssetDisposalRequestRepository disposalRequestRepository;
    private final AssetRepository assetRepository;  // ✅ ADDED THIS
    private final AppUserService userService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final BaseUrlService baseUrlService;

    // ============================================
    // APPROVAL WORKFLOW MANAGEMENT
    // ============================================

    /**
     * Initialize approval workflow for a new disposal request
     * Creates approval records for all mandatory levels
     */
    @Transactional
    public void initializeApprovalWorkflow(Long disposalRequestId) {
        log.info("=== INITIALIZE APPROVAL WORKFLOW ===");
        log.info("📝 Initializing approvals for request: {}", disposalRequestId);

        try {
            AssetDisposalRequest request = disposalRequestRepository.findById(disposalRequestId)
                    .orElseThrow(() -> new RuntimeException("Disposal request not found: " + disposalRequestId));

            // Get all active approval levels ordered by level_order
            List<DisposalApprovalLevel> levels = approvalLevelRepository.findByIsMandatoryTrueOrderByLevelOrderAsc();

            if (levels.isEmpty()) {
                log.warn("⚠️ No approval levels found. Request will skip to execution.");
                request.setCurrentApprovalStep("EXECUTION");
                request.setStatus("PENDING_EXECUTION");
                disposalRequestRepository.save(request);
                return;
            }

            log.info("📋 Found {} mandatory approval levels", levels.size());

            // Create approval records for each level
            for (DisposalApprovalLevel level : levels) {
                DisposalApproval approval = new DisposalApproval();
                approval.setDisposalRequestId(disposalRequestId);
                approval.setLevelOrder(level.getLevelOrder());
                approval.setStatus("PENDING");

                // Set due date based on SLA hours
                LocalDateTime dueDate = LocalDateTime.now().plusHours(level.getSlaHours());
                approval.setDueDate(dueDate);

                // Find users with the required permission
                List<AppUser> approvers = userService.getUsersWithPermission(level.getPermissionName());
                if (!approvers.isEmpty()) {
                    // Assign to the first user with the permission
                    approval.setApproverId(approvers.get(0).getUserId());
                } else {
                    log.warn("⚠️ No users found with permission: {}", level.getPermissionName());
                    // Fallback to admin
                    Optional<AppUser> admin = userService.getUsersByRole("ADMIN").stream().findFirst();
                    admin.ifPresent(adminUser -> approval.setApproverId(adminUser.getUserId()));
                }

                approvalRepository.save(approval);
                log.info("✅ Created approval record for level: {} (Order: {})",
                        level.getLevelName(), level.getLevelOrder());
            }

            // Set the first approval step
            DisposalApprovalLevel firstLevel = levels.get(0);
            request.setCurrentApprovalStep(firstLevel.getLevelName());
            request.setStatus("PENDING_" + firstLevel.getLevelName() + "_APPROVAL");
            disposalRequestRepository.save(request);

            log.info("✅ Approval workflow initialized. First step: {}", firstLevel.getLevelName());

        } catch (Exception e) {
            log.error("❌ Failed to initialize approval workflow: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Process an approval for a specific level
     */
    @Transactional
    public void processApproval(Long disposalRequestId, String levelName, Long approverId,
                                String comment, boolean approved) {
        log.info("=== PROCESS APPROVAL ===");
        log.info("📝 Request: {}, Level: {}, Approver: {}, Approved: {}",
                disposalRequestId, levelName, approverId, approved);

        try {
            AssetDisposalRequest request = disposalRequestRepository.findById(disposalRequestId)
                    .orElseThrow(() -> new RuntimeException("Disposal request not found: " + disposalRequestId));

            // Get the approval level
            DisposalApprovalLevel level = approvalLevelRepository.findByLevelName(levelName)
                    .orElseThrow(() -> new RuntimeException("Approval level not found: " + levelName));

            // Get the approval record
            DisposalApproval approval = approvalRepository
                    .findByDisposalRequestIdAndLevelOrder(disposalRequestId, level.getLevelOrder())
                    .orElseThrow(() -> new RuntimeException("Approval record not found"));

            // Validate current status
            if (!"PENDING".equals(approval.getStatus())) {
                throw new IllegalStateException("Approval already processed. Status: " + approval.getStatus());
            }

            // Validate approver has the required permission
            AppUser approver = userService.getUserById(approverId)
                    .orElseThrow(() -> new RuntimeException("Approver not found"));

            if (!approver.hasPermission(level.getPermissionName()) && !approver.isAdmin()) {
                throw new IllegalStateException("User does not have permission to approve this level");
            }

            // Update approval record
            if (approved) {
                approval.setStatus("APPROVED");
                approval.setApprovedAt(LocalDateTime.now());
                approval.setComment(comment);
                log.info("✅ Approval granted by: {}", approver.getFullName());
            } else {
                approval.setStatus("REJECTED");
                approval.setRejectedAt(LocalDateTime.now());
                approval.setComment(comment);
                log.info("❌ Approval rejected by: {}", approver.getFullName());
            }

            approvalRepository.save(approval);

            // Update the request based on the outcome
            if (approved) {
                handleApprovalGranted(request, level, approverId, comment);
            } else {
                handleApprovalRejected(request, level, approverId, comment);
            }

            // Log audit
            auditService.logAction(
                    approved ? "DISPOSAL_APPROVAL_GRANTED" : "DISPOSAL_APPROVAL_REJECTED",
                    "Disposal request #" + disposalRequestId + " " +
                            (approved ? "approved" : "rejected") + " by " + approver.getFullName() +
                            " at " + level.getLevelName() + " level",
                    approverId
            );

        } catch (Exception e) {
            log.error("❌ Failed to process approval: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // APPROVAL STATUS METHODS
    // ============================================

    /**
     * Get all approvals for a disposal request
     */
    public List<DisposalApproval> getApprovalsByRequestId(Long disposalRequestId) {
        return approvalRepository.findByDisposalRequestIdOrderByLevelOrderAsc(disposalRequestId);
    }

    /**
     * Get pending approvals for a specific user
     */
    public List<DisposalApproval> getPendingApprovalsForUser(Long userId) {
        return approvalRepository.findByApproverIdAndStatus(userId, "PENDING");
    }

    /**
     * Get approval status for a specific level
     */
    public Optional<DisposalApproval> getApprovalForLevel(Long disposalRequestId, String levelName) {
        return approvalLevelRepository.findByLevelName(levelName)
                .flatMap(level -> approvalRepository
                        .findByDisposalRequestIdAndLevelOrder(disposalRequestId, level.getLevelOrder()));
    }

    /**
     * Check if all approvals are complete
     */
    public boolean isAllApprovalsComplete(Long disposalRequestId) {
        List<DisposalApproval> approvals = approvalRepository
                .findByDisposalRequestIdOrderByLevelOrderAsc(disposalRequestId);

        if (approvals.isEmpty()) return false;

        return approvals.stream().allMatch(a -> "APPROVED".equals(a.getStatus()));
    }

    /**
     * Check if any approval was rejected
     */
    public boolean isAnyApprovalRejected(Long disposalRequestId) {
        List<DisposalApproval> approvals = approvalRepository
                .findByDisposalRequestIdOrderByLevelOrderAsc(disposalRequestId);

        return approvals.stream().anyMatch(a -> "REJECTED".equals(a.getStatus()));
    }

    /**
     * Get the current approval step
     */
    public String getCurrentApprovalStep(Long disposalRequestId) {
        List<DisposalApproval> approvals = approvalRepository
                .findByDisposalRequestIdOrderByLevelOrderAsc(disposalRequestId);

        for (DisposalApproval approval : approvals) {
            if ("PENDING".equals(approval.getStatus())) {
                return approvalLevelRepository.findById(approval.getLevelOrder())
                        .map(DisposalApprovalLevel::getLevelName)
                        .orElse("UNKNOWN");
            }
        }

        // All approved or no pending
        if (isAllApprovalsComplete(disposalRequestId)) {
            return "COMPLETED";
        }

        return "UNKNOWN";
    }

    // ============================================
    // ESCALATION METHODS
    // ============================================

    /**
     * Escalate pending approvals that have exceeded their due date
     */
    @Transactional
    public void escalateOverdueApprovals() {
        log.info("=== ESCALATE OVERDUE APPROVALS ===");

        LocalDateTime now = LocalDateTime.now();
        List<DisposalApproval> overdueApprovals = approvalRepository
                .findByStatusAndDueDateBefore("PENDING", now);

        log.info("📋 Found {} overdue approvals", overdueApprovals.size());

        for (DisposalApproval approval : overdueApprovals) {
            try {
                escalateApproval(approval);
            } catch (Exception e) {
                log.error("❌ Failed to escalate approval {}: {}",
                        approval.getApprovalId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void escalateApproval(DisposalApproval approval) {
        log.info("📈 Escalating approval: {}", approval.getApprovalId());

        approval.setEscalatedAt(LocalDateTime.now());
        approval.setEscalationCount(approval.getEscalationCount() + 1);

        // Find the next approver for this level
        DisposalApprovalLevel level = approvalLevelRepository
                .findByLevelOrder(approval.getLevelOrder())
                .orElseThrow(() -> new RuntimeException("Approval level not found"));

        // Find users with the required permission
        List<AppUser> approvers = userService.getUsersWithPermission(level.getPermissionName());

        // Try to find a different approver
        AppUser currentApprover = userService.getUserById(approval.getApproverId()).orElse(null);
        AppUser nextApprover = approvers.stream()
                .filter(a -> !a.getUserId().equals(approval.getApproverId()))
                .findFirst()
                .orElse(currentApprover);

        if (nextApprover != null) {
            approval.setApproverId(nextApprover.getUserId());
            log.info("✅ Escalated to: {}", nextApprover.getFullName());

            // Send escalation notification
            try {
                sendEscalationNotification(approval, level, nextApprover);
            } catch (Exception e) {
                log.error("❌ Failed to send escalation notification: {}", e.getMessage());
            }
        } else {
            log.warn("⚠️ No alternative approver found for level: {}", level.getLevelName());
        }

        approvalRepository.save(approval);

        auditService.logAction("DISPOSAL_APPROVAL_ESCALATED",
                "Disposal approval escalated for request #" + approval.getDisposalRequestId() +
                        " at level " + level.getLevelName() +
                        " to " + (nextApprover != null ? nextApprover.getFullName() : "Unknown"),
                approval.getApproverId()
        );
    }

    // ============================================
    // PRIVATE HELPER METHODS
    // ============================================

    private void handleApprovalGranted(AssetDisposalRequest request, DisposalApprovalLevel level,
                                       Long approverId, String comment) {
        log.info("✅ Handling approval granted for level: {}", level.getLevelName());

        // Update the request's status for this specific level
        switch (level.getLevelName().toUpperCase()) {
            case "FINANCE":
                request.setFinanceApprovedBy(approverId);
                request.setFinanceApprovedAt(LocalDateTime.now());
                request.setFinanceComment(comment);
                request.setFinanceStatus("APPROVED");
                break;
            case "INFRASTRUCTURE":
                request.setInfraApprovedBy(approverId);
                request.setInfraApprovedAt(LocalDateTime.now());
                request.setInfraComment(comment);
                request.setInfraStatus("APPROVED");
                break;
            case "COMPLIANCE":
                request.setComplianceApprovedBy(approverId);
                request.setComplianceApprovedAt(LocalDateTime.now());
                request.setComplianceComment(comment);
                request.setComplianceStatus("APPROVED");
                break;
            default:
                log.warn("⚠️ Unknown approval level: {}", level.getLevelName());
        }

        // Check if all approvals are complete
        if (isAllApprovalsComplete(request.getDisposalRequestId())) {
            log.info("🎉 All approvals complete! Moving to execution.");
            request.setCurrentApprovalStep("EXECUTION");
            request.setStatus("PENDING_EXECUTION");
            request.setApprovalFlowStatus("IN_PROGRESS");

            // Notify execution team
            notifyExecutionTeam(request);

        } else {
            // Move to the next approval step
            String nextStep = getNextApprovalStep(request.getDisposalRequestId());
            if (nextStep != null) {
                request.setCurrentApprovalStep(nextStep);
                request.setStatus("PENDING_" + nextStep + "_APPROVAL");
                log.info("📋 Moving to next step: {}", nextStep);

                // Notify next approvers
                notifyNextApprovers(request, nextStep);
            }
        }

        disposalRequestRepository.save(request);

        // Notify requester of progress
        notifyRequester(request, "Approval granted at " + level.getLevelName() + " level.");
    }

    private void handleApprovalRejected(AssetDisposalRequest request, DisposalApprovalLevel level,
                                        Long approverId, String comment) {
        log.info("❌ Handling approval rejected at level: {}", level.getLevelName());

        // Update the request's status for this specific level
        switch (level.getLevelName().toUpperCase()) {
            case "FINANCE":
                request.setFinanceApprovedBy(approverId);
                request.setFinanceApprovedAt(LocalDateTime.now());
                request.setFinanceComment(comment);
                request.setFinanceStatus("REJECTED");
                break;
            case "INFRASTRUCTURE":
                request.setInfraApprovedBy(approverId);
                request.setInfraApprovedAt(LocalDateTime.now());
                request.setInfraComment(comment);
                request.setInfraStatus("REJECTED");
                break;
            case "COMPLIANCE":
                request.setComplianceApprovedBy(approverId);
                request.setComplianceApprovedAt(LocalDateTime.now());
                request.setComplianceComment(comment);
                request.setComplianceStatus("REJECTED");
                break;
            default:
                log.warn("⚠️ Unknown approval level: {}", level.getLevelName());
        }

        // Mark the entire workflow as rejected
        request.setStatus("REJECTED");
        request.setRejectionReason(level.getLevelName() + " Rejection: " + comment);
        request.setApprovalFlowStatus("REJECTED");
        disposalRequestRepository.save(request);

        // Update asset status back to ACTIVE
        try {
            Asset asset = assetRepository.findById(request.getAssetId()).orElse(null);
            if (asset != null) {
                asset.setDisposalStatus("ACTIVE");
                assetRepository.save(asset);
            }
        } catch (Exception e) {
            log.error("❌ Failed to update asset status: {}", e.getMessage());
        }

        // Notify requester of rejection
        notifyRequester(request, "Your disposal request was rejected at " + level.getLevelName() +
                " level. Reason: " + comment);
    }

    private String getNextApprovalStep(Long disposalRequestId) {
        List<DisposalApproval> approvals = approvalRepository
                .findByDisposalRequestIdOrderByLevelOrderAsc(disposalRequestId);

        for (DisposalApproval approval : approvals) {
            if ("PENDING".equals(approval.getStatus())) {
                return approvalLevelRepository.findByLevelOrder(approval.getLevelOrder())
                        .map(DisposalApprovalLevel::getLevelName)
                        .orElse(null);
            }
        }
        return null;
    }

    // ============================================
    // NOTIFICATION METHODS
    // ============================================

    private void notifyNextApprovers(AssetDisposalRequest request, String nextStep) {
        log.info("📧 Notifying approvers for step: {}", nextStep);

        String permissionName = "DISPOSAL_" + nextStep.toUpperCase() + "_APPROVE";
        List<AppUser> approvers = userService.getUsersWithPermission(permissionName);

        if (approvers.isEmpty()) {
            log.warn("⚠️ No approvers found for permission: {}", permissionName);
            return;
        }

        String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
        String assetName = getAssetName(request.getAssetId());

        for (AppUser user : approvers) {
            try {
                emailService.sendSimpleEmail(
                        user.getEmail(),
                        "Action Required: Disposal Request - " + nextStep + " Approval",
                        generateApprovalEmailBody(request, assetName, nextStep, requestLink)
                );

                notificationService.createNotification(
                        user.getUserId(),
                        "DISPOSAL_" + nextStep.toUpperCase() + "_PENDING",
                        "Disposal Request - " + nextStep + " Approval Required",
                        "Disposal request #" + request.getDisposalRequestId() +
                                " for asset " + assetName + " requires your " + nextStep + " approval.",
                        requestLink
                );
            } catch (Exception e) {
                log.error("❌ Failed to notify user {}: {}", user.getUserId(), e.getMessage());
            }
        }
    }

    private void notifyExecutionTeam(AssetDisposalRequest request) {
        log.info("📧 Notifying execution team for request: {}", request.getDisposalRequestId());

        List<AppUser> executors = userService.getUsersWithPermission("DISPOSAL_EXECUTE");

        if (executors.isEmpty()) {
            log.warn("⚠️ No users with DISPOSAL_EXECUTE permission found.");
            return;
        }

        String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
        String assetName = getAssetName(request.getAssetId());

        for (AppUser user : executors) {
            try {
                emailService.sendSimpleEmail(
                        user.getEmail(),
                        "Action Required: Disposal Request - Ready for Execution",
                        generateExecutionEmailBody(request, assetName, requestLink)
                );

                notificationService.createNotification(
                        user.getUserId(),
                        "DISPOSAL_READY_EXECUTE",
                        "Disposal Request - Ready for Execution",
                        "Disposal request #" + request.getDisposalRequestId() +
                                " for asset " + assetName + " is ready for execution.",
                        requestLink
                );
            } catch (Exception e) {
                log.error("❌ Failed to notify user {}: {}", user.getUserId(), e.getMessage());
            }
        }
    }

    private void notifyRequester(AssetDisposalRequest request, String message) {
        try {
            userService.getUserById(request.getRequestedBy()).ifPresent(user -> {
                String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", request.getDisposalRequestId());
                String assetName = getAssetName(request.getAssetId());

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

                notificationService.createNotification(
                        user.getUserId(),
                        "DISPOSAL_REQUEST_UPDATE",
                        "Disposal Request Update",
                        message + " (Request #" + request.getDisposalRequestId() + ")",
                        requestLink
                );
            });
        } catch (Exception e) {
            log.error("❌ Failed to notify requester: {}", e.getMessage(), e);
        }
    }

    private void sendEscalationNotification(DisposalApproval approval,
                                            DisposalApprovalLevel level,
                                            AppUser approver) {
        try {
            String requestLink = baseUrlService.buildUrl("/admin/disposal/%s", approval.getDisposalRequestId());
            String assetName = getAssetName(
                    disposalRequestRepository.findById(approval.getDisposalRequestId())
                            .map(AssetDisposalRequest::getAssetId)
                            .orElse(null)
            );

            emailService.sendSimpleEmail(
                    approver.getEmail(),
                    "URGENT: Escalated Disposal Approval Required",
                    "This is an escalation notice.\n\n" +
                            "Disposal request requires your attention at the " + level.getLevelName() + " level.\n" +
                            "Request #: " + approval.getDisposalRequestId() + "\n" +
                            "Asset: " + assetName + "\n" +
                            "Previous approver did not respond within the SLA timeframe.\n\n" +
                            "Please review and approve/reject:\n" + requestLink + "\n\n" +
                            "Regards,\nAssetIQ-Pro System"
            );

            notificationService.createNotification(
                    approver.getUserId(),
                    "DISPOSAL_ESCALATED",
                    "URGENT: Escalated Disposal Approval",
                    "Disposal request #" + approval.getDisposalRequestId() +
                            " has been escalated to you at the " + level.getLevelName() + " level.",
                    requestLink
            );
        } catch (Exception e) {
            log.error("❌ Failed to send escalation notification: {}", e.getMessage(), e);
        }
    }

    // ============================================
    // EMAIL BODY GENERATORS
    // ============================================

    private String generateApprovalEmailBody(AssetDisposalRequest request, String assetName,
                                             String step, String requestLink) {
        return "A disposal request requires your " + step + " approval.\n\n" +
                "Request #: " + request.getDisposalRequestId() + "\n" +
                "Asset: " + assetName + "\n" +
                "Disposal Reason: " + request.getDisposalReason() + "\n" +
                "Disposal Method: " + request.getDisposalMethod() + "\n" +
                "Priority: " + request.getPriority() + "\n" +
                "Requested By: " + getRequesterName(request.getRequestedBy()) + "\n\n" +
                "Please review and approve/reject:\n" + requestLink + "\n\n" +
                "Regards,\nAssetIQ-Pro System";
    }

    private String generateExecutionEmailBody(AssetDisposalRequest request, String assetName,
                                              String requestLink) {
        return "A disposal request is ready for execution.\n\n" +
                "Request #: " + request.getDisposalRequestId() + "\n" +
                "Asset: " + assetName + "\n" +
                "Disposal Method: " + request.getDisposalMethod() + "\n\n" +
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

    private String getAssetName(Integer assetId) {
        if (assetId == null) return "Unknown Asset";
        try {
            return assetRepository.findById(assetId)
                    .map(Asset::getName)
                    .orElse("Asset #" + assetId);
        } catch (Exception e) {
            return "Asset #" + assetId;
        }
    }

    private String getRequesterName(Long userId) {
        if (userId == null) return "Unknown";
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
}