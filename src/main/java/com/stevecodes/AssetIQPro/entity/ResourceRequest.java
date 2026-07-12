// ============================================
// ResourceRequest Entity
// ============================================
package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_resource_requests")
@Data
@NoArgsConstructor
public class ResourceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(nullable = false)
    private String description;

    @Column(name = "request_time", nullable = false)
    private LocalDateTime requestTime;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(nullable = false)
    private String status;

    @Column(name = "final_status")
    private String finalStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    @Column(name = "declined_reason")
    private String declinedReason;

    @Column(name = "admin_comment")
    private String adminComment;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
        if (requestTime == null) {
            requestTime = LocalDateTime.now();
        }
    }
}