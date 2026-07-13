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
    private String userEmail;
    private Long roomId;
    private String roomName;
    private String roomType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private LocalDateTime createdAt;

    // For driver bookings
    private Long driverId;
    private String driverName;

    // For creation
    private String purpose;
    private String notes;

    // Who booked the room
    private String bookedBy;
    private String bookedByUsername;
}