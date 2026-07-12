package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "infra_requests")
@Data
@NoArgsConstructor
public class InfraRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "asset_id")
    private Integer assetId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "line_manager_id", nullable = false)
    private Long lineManagerId;

    @Column(name = "resource_type", nullable = false, length = 100)
    private String resourceType;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String specification;

    @Column
    private Integer quantity = 1;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private RequestStatus status = RequestStatus.DRAFT;

    // Approval timestamps
    @Column(name = "lm_approved_at")
    private LocalDateTime lmApprovedAt;

    @Column(name = "lm_approved_by")
    private Long lmApprovedBy;

    @Column(name = "lm_comment", length = 500)
    private String lmComment;

    @Column(name = "infra_reviewed_at")
    private LocalDateTime infraReviewedAt;

    @Column(name = "infra_reviewed_by")
    private Long infraReviewedBy;

    @Column(name = "infra_comment", length = 500)
    private String infraComment;

    @Column(name = "finance_approved_at")
    private LocalDateTime financeApprovedAt;

    @Column(name = "finance_approved_by")
    private Long financeApprovedBy;

    @Column(name = "finance_comment", length = 500)
    private String financeComment;

    @Column(name = "quotation_path", length = 500)
    private String quotationPath;

    @Column(name = "procurement_order_ref", length = 100)
    private String procurementOrderRef;

    @Column(name = "supplier_id")
    private Integer supplierId;

    @Column(name = "purchase_cost", precision = 12, scale = 2)
    private BigDecimal purchaseCost;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "delivered_by")
    private Long deliveredBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "pdf_report_path", length = 500)
    private String pdfReportPath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum RequestStatus {
        DRAFT,
        PENDING_LM_APPROVAL,
        LM_APPROVED,
        LM_REJECTED,
        PENDING_INFRA_REVIEW,
        INFRA_APPROVED,
        INFRA_REJECTED,
        PENDING_FINANCE_APPROVAL,
        FINANCE_APPROVED,
        FINANCE_REJECTED,
        PROCUREMENT,
        DELIVERED,
        COMPLETED,
        CANCELLED
    }

    public boolean isApproved() {
        return status == RequestStatus.COMPLETED ||
                status == RequestStatus.DELIVERED ||
                status == RequestStatus.PROCUREMENT;
    }

    public boolean isPendingApproval() {
        return status == RequestStatus.PENDING_LM_APPROVAL ||
                status == RequestStatus.PENDING_INFRA_REVIEW ||
                status == RequestStatus.PENDING_FINANCE_APPROVAL;
    }
}