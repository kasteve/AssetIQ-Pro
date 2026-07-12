package com.stevecodes.AssetIQPro.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class DriverRequestDTO {

    private Long requestId;
    private Long userId;
    private String userName;
    private Long driverId;
    private String driverName;
    private LocalDateTime requestTime;
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
}