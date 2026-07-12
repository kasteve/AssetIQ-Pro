package com.stevecodes.AssetIQPro.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ResourceRequestDTO {

    private Long requestId;
    private Long userId;
    private String userName;
    private String description;
    private LocalDateTime requestTime;
    private String resourceType;
    private String status;
    private String finalStatus;
    private String requestedBy;
    private LocalDateTime createdAt;

    // Admin response
    private LocalDateTime acceptedAt;
    private LocalDateTime declinedAt;
    private String declinedReason;
    private String adminComment;
}
