package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_driver_requests")
@Data
@NoArgsConstructor
public class DriverRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "driver_id")
    private Long driverId; // -1 = Cab

    @Column(name = "request_time", nullable = false)
    private LocalDateTime requestTime;

    @Column(nullable = false)
    private String status; // PENDING, ACCEPTED, DECLINED, RECALLED, COMPLETED, PENDING_ADMIN

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String destination;

    @Column
    private String reason;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "driver_decision_time")
    private LocalDateTime driverDecisionTime;

    @Column(name = "response_time")
    private LocalDateTime responseTime;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "declined_reason")
    private String declinedReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes; // Stores rating and feedback

    // Trip details
    @Column(name = "pickup_location")
    private String pickupLocation;

    @Column(name = "dropoff_location")
    private String dropoffLocation;

    @Column(name = "trip_start_time")
    private LocalDateTime tripStartTime;

    @Column(name = "trip_end_time")
    private LocalDateTime tripEndTime;

    // Rating fields
    @Column(name = "rating")
    private Integer rating;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

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

    public boolean isCabRequest() {
        return driverId != null && driverId == -1L;
    }
}