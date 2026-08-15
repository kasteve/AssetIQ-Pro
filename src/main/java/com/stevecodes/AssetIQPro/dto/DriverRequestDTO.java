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

    private LocalDate requestDate;
    private LocalTime requestTimeOnly;
    private LocalDateTime pickupDatetime;
    private LocalDateTime dropoffDatetime;

    private String status;
    private String destination;
    private String reason;
    private String requestedBy;
    private LocalDateTime createdAt;

    private LocalDateTime driverDecisionTime;
    private LocalDateTime responseTime;
    private LocalDateTime acceptedAt;
    private String declineReason;
    private String declinedReason;

    private String pickupLocation;
    private String dropoffLocation;
    private LocalDateTime tripStartTime;
    private LocalDateTime tripEndTime;

    private Integer rating;
    private String feedback;
    private String notes;

    // ✅ NEW FIELDS
    private String tripCategory; // TODAY, FUTURE, ADVANCE
    private String tripCategoryDisplay;
    private LocalDateTime expiryTime;
    private Boolean isExpired;

    // SLA Tracking Fields
    private String slaStatus;
    private String slaStatusDisplay;
    private Double slaPercentage;

    // ============================================
    // Helper methods
    // ============================================

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
            case "EXPIRED": return "Expired";
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
            case "EXPIRED": return "danger";
            default: return "secondary";
        }
    }

    public String getRatingDisplay() {
        if (rating == null) return "Not rated";
        return rating + "/5";
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

    public String getTripCategoryDisplay() {
        if (tripCategory == null) return "";
        switch (tripCategory) {
            case "TODAY": return "Today";
            case "ADVANCE": return "Advance Booking";
            case "FUTURE": return "Future";
            default: return tripCategory;
        }
    }

    public String getTripCategoryColor() {
        if (tripCategory == null) return "secondary";
        switch (tripCategory) {
            case "TODAY": return "danger";
            case "ADVANCE": return "warning";
            case "FUTURE": return "info";
            default: return "secondary";
        }
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

    public String getSlaStatusDisplay() {
        if (slaStatus == null) return "N/A";
        switch (slaStatus) {
            case "IN_PROGRESS": return "In Progress";
            case "COMPLETED": return "Completed";
            case "BREACHED": return "Breached";
            case "ESCALATED": return "Escalated";
            default: return slaStatus;
        }
    }

    public String getSlaStatusColor() {
        if (slaStatus == null) return "sla-na";
        switch (slaStatus) {
            case "IN_PROGRESS": return "sla-in-progress";
            case "COMPLETED": return "sla-completed";
            case "BREACHED": return "sla-breached";
            case "ESCALATED": return "sla-escalated";
            default: return "sla-na";
        }
    }
}