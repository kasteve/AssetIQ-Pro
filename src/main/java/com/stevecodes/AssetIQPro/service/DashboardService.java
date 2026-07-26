package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.DashboardStatsDTO;
import com.stevecodes.AssetIQPro.entity.*;
import com.stevecodes.AssetIQPro.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AssetRepository assetRepository;
    private final AssetHistoryRepository historyRepository;
    private final InfraRequestRepository requestRepository;
    private final BookingRepository bookingRepository;
    private final AppUserRepository userRepository;
    private final TransferRepository transferRepository;
    private final DriverRequestRepository driverRequestRepository;

    // ============================================
    // Dashboard Statistics
    // ============================================

    public DashboardStatsDTO getDashboardStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO();

        // Asset stats
        stats.setTotalAssets(assetRepository.count());
        stats.setAvailableAssets(assetRepository.countByStatus(Asset.AssetStatus.AVAILABLE));
        stats.setAssignedAssets(assetRepository.countByStatus(Asset.AssetStatus.ASSIGNED));
        stats.setMaintenanceAssets(assetRepository.countByStatus(Asset.AssetStatus.MAINTENANCE));
        stats.setRetiredAssets(assetRepository.countByStatus(Asset.AssetStatus.RETIRED));
        stats.setTransferredAssets(assetRepository.countByStatus(Asset.AssetStatus.TRANSFERRED));

        // Lifecycle alerts
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(30);
        stats.setWarrantyExpiringSoon(assetRepository.findAssetsWithWarrantyExpiringWithinDays(today, threshold).size());
        stats.setEolSoon(assetRepository.findAssetsWithEOLWithinDays(today, threshold).size());
        stats.setExpiredWarranties(assetRepository.findAssetsWithWarrantyExpiringOnOrBefore(today).size());
        stats.setExpiredEOL(assetRepository.findAssetsWithEOLOnOrBefore(today).size());

        // Request stats
        stats.setPendingRequests(requestRepository.findByStatus(InfraRequest.RequestStatus.PENDING_LM_APPROVAL).size() +
                requestRepository.findByStatus(InfraRequest.RequestStatus.PENDING_INFRA_REVIEW).size() +
                requestRepository.findByStatus(InfraRequest.RequestStatus.PENDING_FINANCE_APPROVAL).size());
        stats.setCompletedRequests(requestRepository.findByStatus(InfraRequest.RequestStatus.COMPLETED).size());
        stats.setTotalRequests((int) requestRepository.count());

        // Booking stats
        stats.setActiveBookings(bookingRepository.countByStatus(Booking.BookingStatus.BOOKED));

        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        stats.setTotalBookingsToday(bookingRepository.countBookingsToday(startOfDay, endOfDay));

        // User stats
        stats.setTotalUsers(userRepository.count());
        stats.setActiveUsers(userRepository.countByActiveTrue());
        stats.setBlockedUsers(userRepository.countByActiveFalse());

        // Charts data
        stats.setAssetsByCategory(getAssetDistribution());
        stats.setAssetsByStatus(getAssetStatusDistribution());
        stats.setAssetsByDepartment(getAssetDistributionByDepartment());
        stats.setRequestsByStatus(getRequestStatusDistribution());
        stats.setRequestsByResourceType(getResourceTypeDistribution());
        stats.setMonthlyTrends(getMonthlyTrends());

        return stats;
    }

    // ============================================
    // Distribution Charts
    // ============================================

    public Map<String, Long> getAssetDistribution() {
        List<Object[]> results = assetRepository.countByCategoryGrouped();
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Object[] result : results) {
            String categoryName = (String) result[0];
            Long count = ((Number) result[1]).longValue();
            distribution.put(categoryName != null ? categoryName : "Uncategorized", count);
        }
        return distribution;
    }

    public Map<String, Long> getAssetStatusDistribution() {
        List<Object[]> results = assetRepository.countByStatusGrouped();
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Object[] result : results) {
            Asset.AssetStatus status = (Asset.AssetStatus) result[0];
            Long count = ((Number) result[1]).longValue();
            distribution.put(status != null ? status.name() : "UNKNOWN", count);
        }
        return distribution;
    }

    public Map<String, Long> getAssetDistributionByDepartment() {
        Map<String, Long> distribution = new LinkedHashMap<>();
        try {
            List<Object[]> results = assetRepository.countByDepartmentGroupedNative();
            if (results != null && !results.isEmpty()) {
                for (Object[] result : results) {
                    String dept = (String) result[0];
                    Long count = ((Number) result[1]).longValue();
                    distribution.put(dept != null && !dept.isEmpty() ? dept : "Unassigned", count);
                }
            }

            if (distribution.isEmpty()) {
                List<Object[]> stringResults = assetRepository.countByCurrentDepartmentGrouped();
                for (Object[] result : stringResults) {
                    String dept = (String) result[0];
                    Long count = ((Number) result[1]).longValue();
                    distribution.put(dept != null && !dept.isEmpty() ? dept : "Unassigned", count);
                }
            }

            if (distribution.isEmpty()) {
                distribution.put("Unassigned", assetRepository.count());
            }
        } catch (Exception e) {
            log.warn("Error getting department distribution: {}", e.getMessage());
            distribution.put("Unassigned", assetRepository.count());
        }
        return distribution;
    }

    private Map<String, Long> getRequestStatusDistribution() {
        List<Object[]> results = requestRepository.countByStatusGrouped();
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Object[] result : results) {
            InfraRequest.RequestStatus status = (InfraRequest.RequestStatus) result[0];
            Long count = ((Number) result[1]).longValue();
            distribution.put(status != null ? status.name() : "UNKNOWN", count);
        }
        return distribution;
    }

    private Map<String, Long> getResourceTypeDistribution() {
        List<Object[]> results = requestRepository.countByResourceTypeGrouped();
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Object[] result : results) {
            String resourceType = (String) result[0];
            Long count = ((Number) result[1]).longValue();
            distribution.put(resourceType != null ? resourceType : "Other", count);
        }
        return distribution;
    }

    // ============================================
    // Trends
    // ============================================

    public List<DashboardStatsDTO.TrendDTO> getMonthlyTrends() {
        LocalDateTime now = LocalDateTime.now();
        List<DashboardStatsDTO.TrendDTO> trends = new ArrayList<>();

        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthStart = now.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1).minusSeconds(1);

            DashboardStatsDTO.TrendDTO trend = new DashboardStatsDTO.TrendDTO();
            trend.setMonth(monthStart.format(DateTimeFormatter.ofPattern("MMM yyyy")));

            trend.setRequests(requestRepository.findByCreatedAtBetween(monthStart, monthEnd).size());

            List<InfraRequest> completed = requestRepository.findByStatusAndDateRange(
                    InfraRequest.RequestStatus.COMPLETED, monthStart, monthEnd);
            trend.setCompleted(completed.size());

            List<AssetHistory> added = historyRepository.findByEventTypeAndDateRange(
                    AssetHistory.EVENT_PROCUREMENT, monthStart, monthEnd);
            trend.setAssetsAdded(added.size());

            trends.add(trend);
        }

        return trends;
    }

    public Map<String, Object> getRequestTrends() {
        Map<String, Object> trends = new HashMap<>();

        List<Map<String, Object>> daily = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(23, 59, 59);

            Map<String, Object> day = new HashMap<>();
            day.put("date", date.toString());
            day.put("count", requestRepository.findByCreatedAtBetween(start, end).size());

            daily.add(day);
        }

        trends.put("daily", daily);
        trends.put("total", requestRepository.count());

        return trends;
    }

    // ============================================
    // Recent Activity Methods - Filtered by User
    // ============================================

    public List<Transfer> getRecentTransfers(Long userId) {
        try {
            // For admin users, return all transfers
            AppUser user = userRepository.findById(userId).orElse(null);
            if (user != null && (user.isAdmin() || user.hasPermission("VIEW_ALL_TRANSACTIONS"))) {
                return transferRepository.findTop5ByOrderByTransferDateDesc();
            }

            // For regular users, filter transfers where they are involved
            return transferRepository.findTop5ByUserId(userId);
        } catch (Exception e) {
            log.error("Error getting recent transfers: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<InfraRequest> getRecentRequests(Long userId) {
        try {
            // ✅ FIXED: Only true admins see everyone's requests.
            // INFRA_REQUEST_VIEW is a default permission granted to ALL users just so
            // they can access the infra requests feature at all - it must NOT be
            // treated as a "view all users' requests" permission.
            AppUser user = userRepository.findById(userId).orElse(null);
            if (user != null && user.isAdmin()) {
                return requestRepository.findTop5ByOrderByCreatedAtDesc();
            }

            // For regular users, only their own requests
            return requestRepository.findTop5ByRequesterId(userId);
        } catch (Exception e) {
            log.error("Error getting recent requests: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<DriverRequest> getRecentDriverRequests(Long userId) {
        try {
            AppUser user = userRepository.findById(userId).orElse(null);
            if (user != null && (user.isAdmin() || user.hasPermission("DRIVER_VIEW"))) {
                return driverRequestRepository.findTop5ByOrderByRequestTimeDesc();
            }
            return driverRequestRepository.findTop5ByUserId(userId);
        } catch (Exception e) {
            log.error("Error getting recent driver requests: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Booking> getRecentBookings(Long userId) {
        try {
            AppUser user = userRepository.findById(userId).orElse(null);
            if (user != null && (user.isAdmin() || user.hasPermission("MANAGE_BOOKINGS"))) {
                return bookingRepository.findTop5ByOrderByStartTimeDesc();
            }
            return bookingRepository.findTop5ByUserId(userId);
        } catch (Exception e) {
            log.error("Error getting recent bookings: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ============================================
    // Daily and Weekly Trends
    // ============================================

    public List<DashboardStatsDTO.TrendDTO> getDailyTrends(int days) {
        LocalDateTime now = LocalDateTime.now();
        List<DashboardStatsDTO.TrendDTO> trends = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime start = now.minusDays(i).toLocalDate().atStartOfDay();
            LocalDateTime end = start.plusDays(1).minusSeconds(1);
            trends.add(buildTrend(start.format(DateTimeFormatter.ofPattern("MMM d")), start, end));
        }
        return trends;
    }

    public List<DashboardStatsDTO.TrendDTO> getWeeklyTrends(int weeks) {
        LocalDateTime now = LocalDateTime.now();
        List<DashboardStatsDTO.TrendDTO> trends = new ArrayList<>();
        for (int i = weeks - 1; i >= 0; i--) {
            LocalDateTime start = now.minusWeeks(i).toLocalDate().with(java.time.DayOfWeek.MONDAY).atStartOfDay();
            LocalDateTime end = start.plusWeeks(1).minusSeconds(1);
            trends.add(buildTrend("Wk " + start.format(DateTimeFormatter.ofPattern("MMM d")), start, end));
        }
        return trends;
    }

    private DashboardStatsDTO.TrendDTO buildTrend(String label, LocalDateTime start, LocalDateTime end) {
        DashboardStatsDTO.TrendDTO t = new DashboardStatsDTO.TrendDTO();
        t.setMonth(label);
        t.setRequests(requestRepository.findByCreatedAtBetween(start, end).size());
        t.setCompleted(requestRepository.findByStatusAndDateRange(InfraRequest.RequestStatus.COMPLETED, start, end).size());
        t.setAssetsAdded(historyRepository.findByEventTypeAndDateRange(AssetHistory.EVENT_PROCUREMENT, start, end).size());
        return t;
    }

    public Map<String, Long> getAssetDistributionByStatusChart() {
        return getAssetStatusDistribution();
    }
}