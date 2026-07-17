package com.stevecodes.AssetIQPro.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ResourceRequestDTO {

    private Long requestId;
    private Long userId;
    private String userName;
    private String description;
    private LocalDateTime requestTime;
    private String resourceType;
    private String status;
    private String finalStatus;
    private String requestedBy;
    private LocalDateTime createdAt;

    // Quantity
    private Integer quantity = 1;

    // Justification
    private String justification;

    // Line Manager
    private Long lineManagerId;
    private Long lmApprovedBy;
    private LocalDateTime lmApprovedAt;
    private String lmComment;

    // Admin response
    private LocalDateTime acceptedAt;
    private LocalDateTime declinedAt;
    private String declinedReason;
    private String adminComment;

    // Acknowledgment
    private LocalDateTime acknowledgedAt;
    private Long acknowledgedBy;
    private String acknowledgedByName;
    private String requesterSignature;

    // Helper methods
    public String getStatusDisplay() {
        if (status == null) return "";
        switch (status) {
            case "PENDING": return "Pending";
            case "PENDING_LM_APPROVAL": return "Pending LM Approval";
            case "PENDING_ADMIN_APPROVAL": return "Pending Admin Approval";
            case "ACCEPTED": return "Accepted";
            case "REJECTED": return "Rejected";
            case "RECALLED": return "Recalled";
            case "COMPLETED": return "Completed";
            default: return status;
        }
    }

    public String getStatusColor() {
        if (status == null) return "secondary";
        switch (status) {
            case "PENDING": return "warning";
            case "PENDING_LM_APPROVAL": return "warning";
            case "PENDING_ADMIN_APPROVAL": return "warning";
            case "ACCEPTED": return "success";
            case "REJECTED": return "danger";
            case "RECALLED": return "secondary";
            case "COMPLETED": return "info";
            default: return "secondary";
        }
    }

    public boolean isPending() {
        return "PENDING".equals(status) || "PENDING_LM_APPROVAL".equals(status) || "PENDING_ADMIN_APPROVAL".equals(status);
    }

    public boolean isAccepted() {
        return "ACCEPTED".equals(status);
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public boolean isRecallable() {
        return "PENDING".equals(status) || "PENDING_LM_APPROVAL".equals(status) || "PENDING_ADMIN_APPROVAL".equals(status);
    }
}