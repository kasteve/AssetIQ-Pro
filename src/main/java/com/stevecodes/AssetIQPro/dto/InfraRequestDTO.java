package com.stevecodes.AssetIQPro.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class InfraRequestDTO {

    // For creation
    @NotNull(message = "Line manager ID is required")
    private Long lineManagerId;

    @NotBlank(message = "Resource type is required")
    private String resourceType;

    private String specification;

    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity = 1;

    private String justification;

    // For response
    private Long requestId;
    private Long requesterId;
    private String requesterName;
    private String requesterDepartment;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer assetId;

    // For procurement
    private Integer supplierId;
    private String supplierName;
    private BigDecimal purchaseCost;
    private String procurementOrderRef;

    // For approval
    private String lmComment;
    private String infraComment;
    private String financeComment;

    // For delivery
    private String deliveryNotes;
    private LocalDateTime deliveredAt;
    private Long deliveredBy;
    private String deliveredByName;

    // For acknowledgment
    private LocalDateTime acknowledgedAt;
    private Long acknowledgedBy;
    private String acknowledgedByName;

    // For PDF report
    private String pdfReportPath;

    // Approval timestamps
    private LocalDateTime lmApprovedAt;
    private LocalDateTime infraReviewedAt;
    private LocalDateTime financeApprovedAt;
    private LocalDateTime completedAt;

    // Approval by IDs
    private Long lmApprovedBy;
    private Long infraReviewedBy;
    private Long financeApprovedBy;

    // Approval names
    private String lmApprovedByName;
    private String infraReviewedByName;
    private String financeApprovedByName;

    // ============================================
    // Status Display
    // ============================================
    public String getStatusDisplay() {
        if (status == null) return "";
        switch (status) {
            case "DRAFT": return "Draft";
            case "PENDING_LM_APPROVAL": return "Pending Line Manager Approval";
            case "LM_APPROVED": return "Approved by Line Manager";
            case "LM_REJECTED": return "Rejected by Line Manager";
            case "PENDING_INFRA_REVIEW": return "Pending Infrastructure Review";
            case "INFRA_APPROVED": return "Approved by Infrastructure";
            case "INFRA_REJECTED": return "Rejected by Infrastructure";
            case "PENDING_FINANCE_APPROVAL": return "Pending Finance Approval";
            case "FINANCE_APPROVED": return "Approved by Finance";
            case "FINANCE_REJECTED": return "Rejected by Finance";
            case "PROCUREMENT": return "In Procurement";
            case "DELIVERED": return "Delivered - Pending Acknowledgment";
            case "COMPLETED": return "Completed";
            case "CANCELLED": return "Cancelled";
            default: return status;
        }
    }

    public String getStatusColor() {
        if (status == null) return "secondary";
        switch (status) {
            case "DRAFT": return "secondary";
            case "PENDING_LM_APPROVAL": return "warning";
            case "LM_APPROVED": return "info";
            case "LM_REJECTED": return "danger";
            case "PENDING_INFRA_REVIEW": return "warning";
            case "INFRA_APPROVED": return "info";
            case "INFRA_REJECTED": return "danger";
            case "PENDING_FINANCE_APPROVAL": return "warning";
            case "FINANCE_APPROVED": return "info";
            case "FINANCE_REJECTED": return "danger";
            case "PROCUREMENT": return "primary";
            case "DELIVERED": return "success";
            case "COMPLETED": return "success";
            case "CANCELLED": return "danger";
            default: return "secondary";
        }
    }
}