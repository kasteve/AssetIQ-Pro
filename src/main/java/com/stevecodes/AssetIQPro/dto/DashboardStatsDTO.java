package com.stevecodes.AssetIQPro.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
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
    private long approvedRequests;
    private long completedRequests;
    private long rejectedRequests;
    private long totalRequests;

    // Booking stats
    private long activeBookings;
    private long totalBookingsToday;
    private long pendingBookings;

    // User stats
    private long totalUsers;
    private long activeUsers;
    private long blockedUsers;

    // Charts data
    private Map<String, Long> assetsByCategory;
    private Map<String, Long> assetsByStatus;
    private Map<String, Long> requestsByStatus;
    private Map<String, Long> requestsByResourceType;
    private List<MonthlyTrendDTO> monthlyTrends;

    @Data
    @NoArgsConstructor
    public static class MonthlyTrendDTO {
        private String month;
        private long requests;
        private long completed;
        private long assetsAdded;
    }
}
