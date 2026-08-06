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
    private String requestedBy;

    // Quantity and Justification
    private Integer quantity = 1;
    private String justification;

    // ✅ NEW: Stock Item Fields
    private Long stockItemId;
    private String stockItemName;
    private Integer currentStockQuantity;

    // Admin response
    private LocalDateTime acceptedAt;
    private LocalDateTime declinedAt;
    private String declinedReason;
    private String adminComment;

    // Line Manager fields
    private Long lineManagerId;
    private String lmApprovedBy;
    private LocalDateTime lmApprovedAt;
    private String lmComment;

    // Completion
    private LocalDateTime completedAt;
    private String deliveryNotes;
    private String finalStatus;

    // Acknowledgment
    private LocalDateTime acknowledgedAt;
    private Long acknowledgedBy;
    private String acknowledgedByName;
    private String requesterSignature;
    private String signatoryName;

    // Signing token
    private String signingToken;
    private LocalDateTime signingTokenExpiry;

    // PDF report path
    private String pdfReportPath;

    // ============================================
    // ✅ SLA Tracking Fields
    // ============================================
    private String slaStatus;
    private String slaStatusDisplay;
    private Double slaPercentage;

    // ============================================
    // Helper Methods
    // ============================================

    public String getStatusDisplay() {
        if (status == null) return "";
        switch (status) {
            case "PENDING": return "Pending";
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
            case "ACCEPTED": return "success";
            case "REJECTED": return "danger";
            case "RECALLED": return "secondary";
            case "COMPLETED": return "info";
            default: return "secondary";
        }
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isAccepted() {
        return "ACCEPTED".equals(status);
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public boolean isRecallable() {
        return "PENDING".equals(status) || "ACCEPTED".equals(status);
    }

    public boolean isSigned() {
        return requesterSignature != null && !requesterSignature.isEmpty();
    }

    // ✅ Stock item helper methods
    public boolean hasStockItem() {
        return stockItemId != null;
    }

    public String getStockInfo() {
        if (stockItemName != null) {
            return stockItemName + (currentStockQuantity != null ? " (Available: " + currentStockQuantity + ")" : "");
        }
        return "No stock item linked";
    }

    // ============================================
    // ✅ SLA Status Helpers
    // ============================================
    public String getSlaStatusDisplay() {
        if (slaStatus == null) return "N/A";
        switch (slaStatus) {
            case "IN_PROGRESS": return "In Progress";
            case "COMPLETED": return "Completed";
            case "BREACHED": return "Breached";
            case "ESCALATED": return "Escalated";
            default: return slaStatus;
        }
    }

    public String getSlaStatusColor() {
        if (slaStatus == null) return "sla-na";
        switch (slaStatus) {
            case "IN_PROGRESS": return "sla-in-progress";
            case "COMPLETED": return "sla-completed";
            case "BREACHED": return "sla-breached";
            case "ESCALATED": return "sla-escalated";
            default: return "sla-na";
        }
    }
}