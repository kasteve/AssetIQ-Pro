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

    @Column(name = "approved_by")
    private Long approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", insertable = false, updatable = false)
    private AppUser approver;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_comment")
    private String approvalComment;

    // ✅ ADDED: Missing field for approval reference
    @Column(name = "approval_reference")
    private String approvalReference;

    @Column(name = "completed_by")
    private Long completedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by", insertable = false, updatable = false)
    private AppUser completer;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completion_notes")
    private String completionNotes;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "data_wipe_confirmed")
    private Boolean dataWipeConfirmed = false;

    @Column(name = "data_wipe_confirmed_by")
    private Long dataWipeConfirmedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_wipe_confirmed_by", insertable = false, updatable = false)
    private AppUser dataWipeConfirmer;

    @Column(name = "data_wipe_confirmed_at")
    private LocalDateTime dataWipeConfirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ============================================
    // Helper Methods
    // ============================================

    public void setApprovalReference(String approvalReference) {
        this.approvalReference = approvalReference;
    }

    public String getApprovalReference() {
        return approvalReference;
    }

    // ============================================
    // Enums
    // ============================================

    public enum DisposalStatus {
        PENDING, APPROVED, REJECTED, IN_PROGRESS, COMPLETED
    }

    public enum DisposalPriority {
        NORMAL, HIGH, URGENT
    }

    public enum DisposalMethod {
        PHYSICAL_DESTRUCTION, DEGAUSSING, OVERWRITE, SHREDDED, RECYCLED, INCINERATION
    }

    // ============================================
    // Display Helpers
    // ============================================

    public String getStatusDisplay() {
        if (status == null) return "";
        switch (status) {
            case "PENDING": return "Pending Approval";
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
            case "PENDING": return "warning";
            case "APPROVED": return "info";
            case "REJECTED": return "danger";
            case "IN_PROGRESS": return "primary";
            case "COMPLETED": return "success";
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
}