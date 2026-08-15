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

    @Column(name = "request_date")
    private LocalDate requestDate;

    @Column(name = "request_time_only")
    private LocalTime requestTimeOnly;

    @Column(name = "pickup_datetime")
    private LocalDateTime pickupDatetime;

    @Column(name = "dropoff_datetime")
    private LocalDateTime dropoffDatetime;

    @Column(nullable = false)
    private String status; // PENDING, PENDING_ADMIN, ACCEPTED, DECLINED, RECALLED, COMPLETED, EXPIRED

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
    private String notes;

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

    // ✅ NEW FIELDS
    @Column(name = "trip_category")
    private String tripCategory; // TODAY, FUTURE, ADVANCE

    @Column(name = "expiry_time")
    private LocalDateTime expiryTime;

    @Column(name = "is_expired")
    private Boolean isExpired = false;

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
        if (isExpired == null) {
            isExpired = false;
        }
        // Auto-populate requestDate and requestTimeOnly from requestTime
        if (requestDate == null && requestTime != null) {
            requestDate = requestTime.toLocalDate();
        }
        if (requestTimeOnly == null && requestTime != null) {
            requestTimeOnly = requestTime.toLocalTime();
        }
        if (pickupDatetime == null && requestTime != null) {
            pickupDatetime = requestTime;
        }
        // ✅ Auto-calculate trip category
        if (requestTime != null) {
            LocalDate today = LocalDate.now();
            LocalDate requestDate = requestTime.toLocalDate();
            if (requestDate.equals(today)) {
                tripCategory = "TODAY";
            } else if (requestDate.isAfter(today) && requestDate.isBefore(today.plusDays(1))) {
                tripCategory = "TODAY";
            } else if (requestDate.isAfter(today)) {
                tripCategory = "ADVANCE";
            } else {
                tripCategory = "FUTURE";
            }
            // Set expiry time: 30 minutes before the trip for TODAY trips
            if ("TODAY".equals(tripCategory)) {
                expiryTime = requestTime.minusMinutes(30);
            }
        }
    }

    public boolean isCabRequest() {
        return driverId != null && driverId == -1L;
    }

    public boolean isTodayTrip() {
        return "TODAY".equals(tripCategory);
    }

    public boolean isAdvanceTrip() {
        return "ADVANCE".equals(tripCategory);
    }

    public boolean isFutureTrip() {
        return "FUTURE".equals(tripCategory);
    }

    public boolean isExpired() {
        return Boolean.TRUE.equals(isExpired);
    }

    public String getFormattedDate() {
        if (requestDate != null) {
            return requestDate.toString();
        }
        return requestTime != null ? requestTime.toLocalDate().toString() : "";
    }

    public String getFormattedTime() {
        if (requestTimeOnly != null) {
            return requestTimeOnly.toString();
        }
        return requestTime != null ? requestTime.toLocalTime().toString() : "";
    }

    public boolean isFutureBooking() {
        if (requestDate == null) {
            return false;
        }
        return requestDate.isAfter(LocalDate.now()) ||
                (requestDate.isEqual(LocalDate.now()) &&
                        requestTimeOnly != null &&
                        requestTimeOnly.isAfter(LocalTime.now()));
    }

    public boolean isToday() {
        if (requestDate == null) {
            return false;
        }
        return requestDate.isEqual(LocalDate.now());
    }

    public void setTripType(String tripType) {
    }
}