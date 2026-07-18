package com.stevecodes.AssetIQPro.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class BookingDTO {

    private Long bookingId;
    private Long userId;
    private String userName;
    private Long roomId;
    private String roomName;
    private String roomType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
    private String status;
    private String purpose;
    private String notes;
    private String bookedBy;
    private String bookedByUsername;
    private Long driverId;
    private Integer roleId;
    private boolean currentlyActive;
    private boolean active;

    // Server Room fields
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long declinedBy;
    private LocalDateTime declinedAt;
    private String declinedReason;

    // Server Room Comments
    private String infraComment;
    private String requesterComment;

    // Signout fields
    private String signoutToken;
    private LocalDateTime signoutTokenExpiry;
    private LocalDateTime signedOutAt;
    private String signature;
    private String signatoryName;

    // Slot Request fields
    private Long slotRequestUserId;
    private String slotRequestUserName;
    private LocalDateTime slotRequestAt;
    private String slotRequestStatus;

    public String getStatusDisplay() {
        if (status == null) return "";
        switch (status) {
            case "PENDING": return "Pending Approval";
            case "BOOKED": return "Booked";
            case "CONFIRMED": return "Completed";
            case "CANCELLED": return "Cancelled";
            case "ACTIVE": return "In Use";
            default: return status;
        }
    }

    public String getStatusColor() {
        if (status == null) return "secondary";
        switch (status) {
            case "PENDING": return "warning";
            case "BOOKED": return "success";
            case "CONFIRMED": return "info";
            case "CANCELLED": return "danger";
            case "ACTIVE": return "primary";
            default: return "secondary";
        }
    }
}