package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.DashboardStatsDTO;
import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.AssetHistory;
import com.stevecodes.AssetIQPro.entity.Booking;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.entity.Transfer;
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
        Map<String, Long> distribution = new HashMap<>();
        for (Object[] result : results) {
            String categoryName = (String) result[0];
            Long count = (Long) result[1];
            distribution.put(categoryName != null ? categoryName : "Uncategorized", count);
        }
        return distribution;
    }

    private Map<String, Long> getAssetStatusDistribution() {
        List<Object[]> results = assetRepository.countByStatusGrouped();
        return results.stream()
                .collect(Collectors.toMap(
                        r -> ((Asset.AssetStatus) r[0]).name(),
                        r -> (Long) r[1]
                ));
    }

    private Map<String, Long> getRequestStatusDistribution() {
        List<Object[]> results = requestRepository.countByStatusGrouped();
        return results.stream()
                .collect(Collectors.toMap(
                        r -> ((InfraRequest.RequestStatus) r[0]).name(),
                        r -> (Long) r[1]
                ));
    }

    private Map<String, Long> getResourceTypeDistribution() {
        List<Object[]> results = requestRepository.countByResourceTypeGrouped();
        return results.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1]
                ));
    }

    // ============================================
    // Trends
    // ============================================

    public List<DashboardStatsDTO.MonthlyTrendDTO> getMonthlyTrends() {
        LocalDateTime now = LocalDateTime.now();
        List<DashboardStatsDTO.MonthlyTrendDTO> trends = new ArrayList<>();

        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthStart = now.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1).minusSeconds(1);

            DashboardStatsDTO.MonthlyTrendDTO trend = new DashboardStatsDTO.MonthlyTrendDTO();
            trend.setMonth(monthStart.format(DateTimeFormatter.ofPattern("MMM yyyy")));

            // Count requests in this month
            trend.setRequests(requestRepository.findByCreatedAtBetween(monthStart, monthEnd).size());

            // Count completed in this month
            List<InfraRequest> completed = requestRepository.findByStatusAndDateRange(
                    InfraRequest.RequestStatus.COMPLETED, monthStart, monthEnd);
            trend.setCompleted(completed.size());

            // Count assets added in this month (from AssetHistory)
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
    // Recent Activity Methods - FIXED
    // ============================================

    public List<Transfer> getRecentTransfers() {
        try {
            return transferRepository.findTop5ByOrderByTransferDateDesc();
        } catch (Exception e) {
            log.error("Error getting recent transfers: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<InfraRequest> getRecentRequests() {
        try {
            return requestRepository.findTop5ByOrderByCreatedAtDesc();
        } catch (Exception e) {
            log.error("Error getting recent requests: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}