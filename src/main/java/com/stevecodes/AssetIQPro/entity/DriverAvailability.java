package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "driver_availability")
@Data
@NoArgsConstructor
public class DriverAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "status")
    private String status = "AVAILABLE"; // AVAILABLE, BUSY, OFF_DUTY

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "current_location")
    private String currentLocation;

    @Column(name = "vehicle_registration")
    private String vehicleRegistration;

    @Column(name = "vehicle_type")
    private String vehicleType;

    // ✅ NEW: Available Date (for scheduling)
    @Column(name = "available_date")
    private LocalDate availableDate;

    // ✅ NEW: Available Start Time
    @Column(name = "available_start_time")
    private LocalTime availableStartTime;

    // ✅ NEW: Available End Time
    @Column(name = "available_end_time")
    private LocalTime availableEndTime;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = "AVAILABLE";
        }
        if (lastUpdated == null) {
            lastUpdated = LocalDateTime.now();
        }
        // ✅ Auto-populate availableDate from startTime
        if (availableDate == null && startTime != null) {
            availableDate = startTime.toLocalDate();
        }
        if (availableStartTime == null && startTime != null) {
            availableStartTime = startTime.toLocalTime();
        }
        if (availableEndTime == null && endTime != null) {
            availableEndTime = endTime.toLocalTime();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
        // ✅ Update derived fields if startTime/endTime changed
        if (startTime != null) {
            if (availableDate == null) {
                availableDate = startTime.toLocalDate();
            }
            if (availableStartTime == null) {
                availableStartTime = startTime.toLocalTime();
            }
        }
        if (endTime != null && availableEndTime == null) {
            availableEndTime = endTime.toLocalTime();
        }
    }

    // ✅ Helper method to check if driver is available at a specific date and time
    public boolean isAvailableAt(LocalDate date, LocalTime time) {
        if (!"AVAILABLE".equals(status)) {
            return false;
        }
        if (availableDate != null && !availableDate.equals(date)) {
            return false;
        }
        if (availableStartTime != null && time.isBefore(availableStartTime)) {
            return false;
        }
        if (availableEndTime != null && time.isAfter(availableEndTime)) {
            return false;
        }
        return true;
    }

    // ✅ Helper method to check if driver is available on a specific date
    public boolean isAvailableOn(LocalDate date) {
        if (!"AVAILABLE".equals(status)) {
            return false;
        }
        return availableDate == null || availableDate.equals(date);
    }
}