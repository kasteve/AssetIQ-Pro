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
    private long pendingPasswordChange;

    // Charts data
    private Map<String, Long> assetsByCategory;
    private Map<String, Long> assetsByStatus;
    private Map<String, Long> requestsByStatus;
    private Map<String, Long> requestsByResourceType;
    private Map<String, Long> assetsByDepartment;
    private Map<String, Long> assetsByAssetType;
    private List<TrendDTO> monthlyTrends;
    private List<TrendDTO> dailyTrends;
    private List<TrendDTO> weeklyTrends;

    @Data
    @NoArgsConstructor
    public static class TrendDTO {
        private String month; // used as generic label for day/week/month
        private long requests;
        private long completed;
        private long assetsAdded;
    }
}