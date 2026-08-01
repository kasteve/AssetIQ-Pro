package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_bookings")
@Data
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "driver_id")
    private Long driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BookingStatus status = BookingStatus.BOOKED;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ✅ ADDED: Room Type field
    @Column(name = "room_type")
    private String roomType;

    // ✅ ADDED: Room Name field (for denormalization or query)
    @Column(name = "room_name")
    private String roomName;

    // Server Room specific fields
    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "declined_by")
    private Long declinedBy;

    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    @Column(name = "declined_reason")
    private String declinedReason;

    // Server Room Comments
    @Column(name = "infra_comment", columnDefinition = "TEXT")
    private String infraComment;

    @Column(name = "requester_comment", columnDefinition = "TEXT")
    private String requesterComment;

    @Column(name = "signout_token")
    private String signoutToken;

    @Column(name = "signout_token_expiry")
    private LocalDateTime signoutTokenExpiry;

    @Column(name = "signed_out_at")
    private LocalDateTime signedOutAt;

    @Column(name = "signature")
    private String signature;

    @Column(name = "signatory_name")
    private String signatoryName;

    // Slot Request fields
    @Column(name = "slot_request_user_id")
    private Long slotRequestUserId;

    @Column(name = "slot_request_user_name")
    private String slotRequestUserName;

    @Column(name = "slot_request_at")
    private LocalDateTime slotRequestAt;

    @Column(name = "slot_request_status")
    private String slotRequestStatus; // PENDING, APPROVED, DECLINED

    // ✅ FIXED: getRoomName() now returns roomName
    public String getRoomName() {
        return roomName != null ? roomName : "Room " + roomId;
    }

    public enum BookingStatus {
        PENDING,      // Waiting for approval (Server Room)
        BOOKED,       // Confirmed booking
        CONFIRMED,    // Completed/Signed out
        CANCELLED,    // Cancelled
        ACTIVE        // Currently in use (Server Room)
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = BookingStatus.BOOKED;
        }
        // Set roomType from room if not set
        if (roomType == null && roomId != null) {
            // This will be set by the service when saving
        }
    }

    public String getRoomNumber() {
        return "Room " + roomId;
    }

    public String getBookedBy() {
        return "User " + userId;
    }

    public LocalDateTime getBookingTime() {
        return createdAt;
    }
}