package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tbl_resource_requests")
public class ResourceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "quantity")
    private Integer quantity = 1;

    @Column(name = "justification", columnDefinition = "TEXT")
    private String justification;

    @Column(name = "request_time", nullable = false)
    private LocalDateTime requestTime;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, ACCEPTED, REJECTED, COMPLETED, RECALLED

    // ✅ Stock Item Link
    @Column(name = "stock_item_id")
    private Long stockItemId;

    @Column(name = "stock_item_name")
    private String stockItemName;

    // Admin fields
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    @Column(name = "declined_reason")
    private String declinedReason;

    @Column(name = "admin_comment", columnDefinition = "TEXT")
    private String adminComment;

    // Line Manager fields
    @Column(name = "line_manager_id")
    private Long lineManagerId;

    @Column(name = "lm_approved_at")
    private LocalDateTime lmApprovedAt;

    @Column(name = "lm_approved_by")
    private String lmApprovedBy;

    @Column(name = "lm_comment")
    private String lmComment;

    // Completion fields
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "delivery_notes")
    private String deliveryNotes;

    @Column(name = "final_status")
    private String finalStatus;

    // Acknowledgment fields
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "requester_signature", columnDefinition = "TEXT")
    private String requesterSignature;

    @Column(name = "signatory_name")
    private String signatoryName;

    // Signing token fields
    @Column(name = "signing_token")
    private String signingToken;

    @Column(name = "signing_token_expiry")
    private LocalDateTime signingTokenExpiry;

    @Column(name = "pdf_report_path")
    private String pdfReportPath;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (requestTime == null) {
            requestTime = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
        if (quantity == null) {
            quantity = 1;
        }
    }

    public boolean isSigned() {
        return requesterSignature != null && !requesterSignature.isEmpty();
    }
}