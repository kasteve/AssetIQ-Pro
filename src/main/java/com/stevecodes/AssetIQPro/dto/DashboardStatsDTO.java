package com.stevecodes.AssetIQPro.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DashboardStatsDTO {
    // Asset stats
    private long totalAssets;
    private long availableAssets;
    private long assignedAssets;
    private long maintenanceAssets;
    private long retiredAssets;
    private long transferredAssets;

    // Lifecycle alerts
    private long warrantyExpiringSoon;
    private long eolSoon;
    private long expiredWarranties;
    private long expiredEOL;

    // Request stats
    private long pendingRequests;
    private long completedRequests;
    private long totalRequests;

    // Booking stats
    private long activeBookings;
    private long totalBookingsToday;

    // User stats
    private long totalUsers;
    private long activeUsers;
    private long blockedUsers;

    // Chart data
    private Map<String, Long> assetsByCategory;
    private Map<String, Long> assetsByStatus;
    private Map<String, Long> assetsByDepartment;  // ADD THIS FIELD
    private Map<String, Long> requestsByStatus;
    private Map<String, Long> requestsByResourceType;
    private List<TrendDTO> monthlyTrends;

    @Data
    public static class TrendDTO {
        private String month;
        private int requests;
        private int completed;
        private int assetsAdded;
    }
}