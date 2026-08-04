package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_disposal_requests")
@Data
@NoArgsConstructor
public class AssetDisposalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "disposal_request_id")
    private Long disposalRequestId;

    @Column(name = "asset_id", nullable = false)
    private Integer assetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    private Asset asset;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", insertable = false, updatable = false)
    private AppUser requester;

    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "disposal_reason", nullable = false)
    private String disposalReason;

    @Column(name = "disposal_method", nullable = false)
    private String disposalMethod;

    @Column(name = "priority")
    private String priority = "NORMAL";

    @Column(name = "status")
    private String status = "PENDING";

    // ============================================
    // LEGACY APPROVAL FIELDS (Keep for backward compatibility)
    // ============================================
    @Column(name = "approved_by")
    private Long approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", insertable = false, updatable = false)
    private AppUser approver;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_comment")
    private String approvalComment;

    @Column(name = "approval_reference")
    private String approvalReference;

    // ============================================
    // FINANCE APPROVAL
    // ============================================
    @Column(name = "finance_approved_by")
    private Long financeApprovedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finance_approved_by", insertable = false, updatable = false)
    private AppUser financeApprover;

    @Column(name = "finance_approved_at")
    private LocalDateTime financeApprovedAt;

    @Column(name = "finance_comment", columnDefinition = "NVARCHAR(MAX)")
    private String financeComment;

    @Column(name = "finance_status")
    private String financeStatus = "PENDING";

    // ============================================
    // INFRASTRUCTURE APPROVAL
    // ============================================
    @Column(name = "infra_approved_by")
    private Long infraApprovedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "infra_approved_by", insertable = false, updatable = false)
    private AppUser infraApprover;

    @Column(name = "infra_approved_at")
    private LocalDateTime infraApprovedAt;

    @Column(name = "infra_comment", columnDefinition = "NVARCHAR(MAX)")
    private String infraComment;

    @Column(name = "infra_status")
    private String infraStatus = "PENDING";

    @Column(name = "disposal_policy_path")
    private String disposalPolicyPath;

    // ============================================
    // RISK & COMPLIANCE APPROVAL
    // ============================================
    @Column(name = "compliance_approved_by")
    private Long complianceApprovedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compliance_approved_by", insertable = false, updatable = false)
    private AppUser complianceApprover;

    @Column(name = "compliance_approved_at")
    private LocalDateTime complianceApprovedAt;

    @Column(name = "compliance_comment", columnDefinition = "NVARCHAR(MAX)")
    private String complianceComment;

    @Column(name = "compliance_status")
    private String complianceStatus = "PENDING";

    // ============================================
    // FINAL EXECUTION
    // ============================================
    @Column(name = "executed_by")
    private Long executedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executed_by", insertable = false, updatable = false)
    private AppUser executor;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "proof_document_path")
    private String proofDocumentPath;

    // ============================================
    // WORKFLOW TRACKING
    // ============================================
    @Column(name = "current_approval_step")
    private String currentApprovalStep = "FINANCE";

    @Column(name = "approval_flow_status")
    private String approvalFlowStatus = "IN_PROGRESS";

    // ============================================
    // REJECTION
    // ============================================
    @Column(name = "rejection_reason")
    private String rejectionReason;

    // ============================================
    // COMPLETION
    // ============================================
    @Column(name = "completed_by")
    private Long completedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by", insertable = false, updatable = false)
    private AppUser completer;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completion_notes")
    private String completionNotes;

    // ============================================
    // DATA WIPE
    // ============================================
    @Column(name = "data_wipe_confirmed")
    private Boolean dataWipeConfirmed = false;

    @Column(name = "data_wipe_confirmed_by")
    private Long dataWipeConfirmedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_wipe_confirmed_by", insertable = false, updatable = false)
    private AppUser dataWipeConfirmer;

    @Column(name = "data_wipe_confirmed_at")
    private LocalDateTime dataWipeConfirmedAt;

    // ============================================
    // TIMESTAMPS
    // ============================================
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ============================================
    // ENUMS
    // ============================================
    public enum ApprovalStep {
        FINANCE, INFRASTRUCTURE, COMPLIANCE, EXECUTION, COMPLETED
    }

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    public void setApprovalReference(String approvalReference) {
        this.approvalReference = approvalReference;
    }

    public String getApprovalReference() {
        return approvalReference;
    }

    // ============================================
    // STATUS DISPLAY HELPERS
    // ============================================

    public String getStatusDisplay() {
        if (status == null) return "";
        switch (status) {
            case "PENDING_FINANCE_APPROVAL": return "Pending Finance";
            case "PENDING_INFRA_APPROVAL": return "Pending Infrastructure";
            case "PENDING_COMPLIANCE_APPROVAL": return "Pending Compliance";
            case "PENDING_EXECUTION": return "Pending Execution";
            case "APPROVED": return "Approved";
            case "REJECTED": return "Rejected";
            case "IN_PROGRESS": return "In Progress";
            case "COMPLETED": return "Completed";
            default: return status;
        }
    }

    public String getStatusColor() {
        if (status == null) return "secondary";
        switch (status) {
            case "PENDING_FINANCE_APPROVAL":
            case "PENDING_INFRA_APPROVAL":
            case "PENDING_COMPLIANCE_APPROVAL":
                return "warning";
            case "PENDING_EXECUTION":
                return "info";
            case "APPROVED":
                return "success";
            case "REJECTED":
                return "danger";
            case "IN_PROGRESS":
                return "primary";
            case "COMPLETED":
                return "success";
            default: return "secondary";
        }
    }

    public String getPriorityDisplay() {
        if (priority == null) return "Normal";
        switch (priority.toUpperCase()) {
            case "URGENT": return "🔴 Urgent";
            case "HIGH": return "🟠 High";
            case "NORMAL": return "🟢 Normal";
            default: return priority;
        }
    }

    public String getPriorityColor() {
        if (priority == null) return "secondary";
        switch (priority.toUpperCase()) {
            case "URGENT": return "danger";
            case "HIGH": return "warning";
            case "NORMAL": return "success";
            default: return "secondary";
        }
    }

    public String getDisposalMethodDisplay() {
        if (disposalMethod == null) return "";
        switch (disposalMethod.toUpperCase()) {
            case "PHYSICAL_DESTRUCTION": return "Physical Destruction";
            case "DEGAUSSING": return "Degaussing";
            case "OVERWRITE": return "Overwrite";
            case "SHREDDED": return "Shredded";
            case "RECYCLED": return "Recycled";
            case "INCINERATION": return "Incineration";
            default: return disposalMethod;
        }
    }

    public String getCurrentStepDisplay() {
        if (currentApprovalStep == null) return "Not Started";
        switch (currentApprovalStep.toUpperCase()) {
            case "FINANCE": return "Finance Approval";
            case "INFRASTRUCTURE": return "Infrastructure Approval";
            case "COMPLIANCE": return "Risk & Compliance Approval";
            case "EXECUTION": return "Execution";
            case "COMPLETED": return "Completed";
            default: return currentApprovalStep;
        }
    }

    public boolean isFinanceApproved() {
        return "APPROVED".equals(financeStatus);
    }

    public boolean isInfraApproved() {
        return "APPROVED".equals(infraStatus);
    }

    public boolean isComplianceApproved() {
        return "APPROVED".equals(complianceStatus);
    }

    public boolean isAllApproved() {
        return isFinanceApproved() && isInfraApproved() && isComplianceApproved();
    }

    public boolean isApprovalFlowComplete() {
        return "COMPLETED".equals(approvalFlowStatus);
    }
}