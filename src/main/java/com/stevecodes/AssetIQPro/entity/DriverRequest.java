package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

    // ✅ NEW: Request Date (separate from time)
    @Column(name = "request_date")
    private LocalDate requestDate;

    // ✅ NEW: Request Time Only (separate from date)
    @Column(name = "request_time_only")
    private LocalTime requestTimeOnly;

    // ✅ NEW: Pickup Datetime (full datetime for pickup)
    @Column(name = "pickup_datetime")
    private LocalDateTime pickupDatetime;

    // ✅ NEW: Dropoff Datetime (full datetime for dropoff)
    @Column(name = "dropoff_datetime")
    private LocalDateTime dropoffDatetime;

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
        // ✅ Auto-populate requestDate and requestTimeOnly from requestTime
        if (requestDate == null && requestTime != null) {
            requestDate = requestTime.toLocalDate();
        }
        if (requestTimeOnly == null && requestTime != null) {
            requestTimeOnly = requestTime.toLocalTime();
        }
        // Auto-populate pickupDatetime from requestTime if not set
        if (pickupDatetime == null && requestTime != null) {
            pickupDatetime = requestTime;
        }
    }

    public boolean isCabRequest() {
        return driverId != null && driverId == -1L;
    }

    // ✅ Helper method to get formatted date
    public String getFormattedDate() {
        if (requestDate != null) {
            return requestDate.toString();
        }
        return requestTime != null ? requestTime.toLocalDate().toString() : "";
    }

    // ✅ Helper method to get formatted time
    public String getFormattedTime() {
        if (requestTimeOnly != null) {
            return requestTimeOnly.toString();
        }
        return requestTime != null ? requestTime.toLocalTime().toString() : "";
    }

    // ✅ Helper method to check if booking is in the future
    public boolean isFutureBooking() {
        if (requestDate == null) {
            return false;
        }
        return requestDate.isAfter(LocalDate.now()) ||
                (requestDate.isEqual(LocalDate.now()) &&
                        requestTimeOnly != null &&
                        requestTimeOnly.isAfter(LocalTime.now()));
    }

    // ✅ Helper method to check if booking is today
    public boolean isToday() {
        if (requestDate == null) {
            return false;
        }
        return requestDate.isEqual(LocalDate.now());
    }
}