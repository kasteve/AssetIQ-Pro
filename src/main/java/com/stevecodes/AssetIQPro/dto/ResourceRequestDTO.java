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

    // Admin response
    private LocalDateTime acceptedAt;
    private LocalDateTime declinedAt;
    private String declinedReason;
    private String adminComment;

    // Completion
    private LocalDateTime completedAt;
    private String deliveryNotes;

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

    // Helper methods
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
}