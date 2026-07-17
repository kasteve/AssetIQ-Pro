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
@Table(name = "resource_requests")
public class ResourceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "requestId")
    private Long requestId;

    @Column(name = "userId", nullable = false)
    private Long userId;

    @Column(name = "requestedBy")
    private String requestedBy;

    @Column(name = "resourceType", nullable = false)
    private String resourceType;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "quantity")
    private Integer quantity = 1;

    @Column(name = "justification", columnDefinition = "TEXT")
    private String justification;

    @Column(name = "requestTime", nullable = false)
    private LocalDateTime requestTime;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, ACCEPTED, REJECTED, COMPLETED, RECALLED

    // Admin fields
    @Column(name = "acceptedAt")
    private LocalDateTime acceptedAt;

    @Column(name = "declinedAt")
    private LocalDateTime declinedAt;

    @Column(name = "declinedReason")
    private String declinedReason;

    @Column(name = "adminComment", columnDefinition = "TEXT")
    private String adminComment;

    // Completion fields
    @Column(name = "completedAt")
    private LocalDateTime completedAt;

    @Column(name = "deliveryNotes", columnDefinition = "TEXT")
    private String deliveryNotes;

    // Acknowledgment fields
    @Column(name = "acknowledgedAt")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledgedBy")
    private Long acknowledgedBy;

    @Column(name = "requesterSignature", columnDefinition = "TEXT")
    private String requesterSignature; // Base64 encoded image

    @Column(name = "signatoryName")
    private String signatoryName;

    // Signing token fields (for email link)
    @Column(name = "signingToken")
    private String signingToken;

    @Column(name = "signingTokenExpiry")
    private LocalDateTime signingTokenExpiry;

    // PDF report path
    @Column(name = "pdfReportPath")
    private String pdfReportPath;

    @Column(name = "createdAt", updatable = false)
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