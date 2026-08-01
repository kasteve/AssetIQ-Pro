package com.stevecodes.AssetIQPro.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
public class DriverRequestDTO {

    private Long requestId;
    private Long userId;
    private String userName;
    private Long driverId;
    private String driverName;
    private LocalDateTime requestTime;

    // ✅ NEW: Date and Time fields
    private LocalDate requestDate;
    private LocalTime requestTimeOnly;
    private LocalDateTime pickupDatetime;
    private LocalDateTime dropoffDatetime;

    private String status;
    private String destination;
    private String reason;
    private String requestedBy;
    private LocalDateTime createdAt;

    // Driver decision
    private LocalDateTime driverDecisionTime;
    private LocalDateTime responseTime;
    private LocalDateTime acceptedAt;
    private String declineReason;
    private String declinedReason;

    // Trip details
    private String pickupLocation;
    private String dropoffLocation;
    private LocalDateTime tripStartTime;
    private LocalDateTime tripEndTime;

    // Rating
    private Integer rating;
    private String feedback;
    private String notes;

    // Helper methods
    public boolean isCabRequest() {
        return driverId != null && driverId == -1L;
    }

    public String getStatusDisplay() {
        if (status == null) return "";
        switch (status) {
            case "PENDING": return "Pending";
            case "PENDING_ADMIN": return "Pending Admin";
            case "ACCEPTED": return "Accepted";
            case "DECLINED": return "Declined";
            case "RECALLED": return "Recalled";
            case "COMPLETED": return "Completed";
            default: return status;
        }
    }

    public String getStatusColor() {
        if (status == null) return "secondary";
        switch (status) {
            case "PENDING": return "warning";
            case "PENDING_ADMIN": return "info";
            case "ACCEPTED": return "success";
            case "DECLINED": return "danger";
            case "RECALLED": return "secondary";
            case "COMPLETED": return "primary";
            default: return "secondary";
        }
    }

    public String getRatingDisplay() {
        if (rating == null) return "Not rated";
        return rating + "/5";
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
}