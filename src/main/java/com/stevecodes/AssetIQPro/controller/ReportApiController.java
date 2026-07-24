package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.*;
import com.stevecodes.AssetIQPro.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('VIEW_REPORTS', 'ADMIN')")
public class ReportApiController {

    private final AssetRepository assetRepository;
    private final TransferRepository transferRepository;
    private final DriverRequestRepository driverRequestRepository;
    private final BookingRepository bookingRepository;
    private final AuditLogRepository auditLogRepository;
    private final CategoryRepository categoryRepository;
    private final AppUserRepository userRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;

    // ============================================
    // 1. END OF LIFE REPORT
    // ============================================
    @GetMapping("/eol")
    public Map<String, Object> getEOLReport(
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        Map<String, Object> response = new HashMap<>();
        List<Asset> assets = assetRepository.findAll();

        // Apply filters
        if (category != null) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().getCategoryId().equals(category))
                    .collect(Collectors.toList());
        }

        if (status != null && !status.isEmpty()) {
            assets = assets.stream()
                    .filter(a -> a.getStatus() != null && a.getStatus().name().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        if (dateFrom != null) {
            assets = assets.stream()
                    .filter(a -> a.getEolDate() == null || !a.getEolDate().isBefore(dateFrom))
                    .collect(Collectors.toList());
        }

        if (dateTo != null) {
            assets = assets.stream()
                    .filter(a -> a.getEolDate() == null || !a.getEolDate().isAfter(dateTo))
                    .collect(Collectors.toList());
        }

        long eolCount = assets.stream()
                .filter(a -> a.getStatus() == Asset.AssetStatus.RETIRED)
                .count();

        long activeCount = assets.stream()
                .filter(a -> a.getStatus() == Asset.AssetStatus.AVAILABLE || a.getStatus() == Asset.AssetStatus.ASSIGNED)
                .count();

        // Build response
        List<Map<String, Object>> assetList = assets.stream()
                .map(a -> {
                    Map<String, Object> item = new HashMap<>();
                    Object assetNameObj = a.getName();
                    item.put("name", assetNameObj != null ? assetNameObj.toString() : "N/A");
                    item.put("tag", a.getTag() != null ? a.getTag() : "N/A");
                    item.put("category", a.getCategory() != null ? a.getCategory().getName() : "N/A");
                    item.put("purchaseDate", a.getPurchaseDate() != null ? a.getPurchaseDate().toString() : "N/A");
                    item.put("eolDate", a.getEolDate() != null ? a.getEolDate().toString() : "N/A");
                    item.put("status", a.getStatus() != null ? a.getStatus().name() : "UNKNOWN");

                    if (a.getEolDate() != null) {
                        long days = ChronoUnit.DAYS.between(LocalDate.now(), a.getEolDate());
                        item.put("daysRemaining", days > 0 ? days + " days" : (days == 0 ? "Today" : Math.abs(days) + " days overdue"));
                    } else {
                        item.put("daysRemaining", "N/A");
                    }
                    return item;
                })
                .collect(Collectors.toList());

        response.put("total", assets.size());
        response.put("eolCount", eolCount);
        response.put("eosCount", 0);
        response.put("activeCount", activeCount);
        response.put("assets", assetList);
        return response;
    }

    // ============================================
    // 2. WARRANTY EXPIRY REPORT
    // ============================================
    @GetMapping("/warranty")
    public Map<String, Object> getWarrantyReport(
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        Map<String, Object> response = new HashMap<>();
        List<Asset> assets = assetRepository.findAll();

        // Apply filters
        if (category != null) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().getCategoryId().equals(category))
                    .collect(Collectors.toList());
        }

        LocalDate now = LocalDate.now();

        // Determine warranty status
        List<Map<String, Object>> assetList = new ArrayList<>();
        long activeCount = 0;
        long expiringCount = 0;
        long expiredCount = 0;

        for (Asset asset : assets) {
            Map<String, Object> item = new HashMap<>();
            Object assetNameObj = asset.getName();
            item.put("name", assetNameObj != null ? assetNameObj.toString() : "N/A");
            item.put("tag", asset.getTag() != null ? asset.getTag() : "N/A");
            item.put("category", asset.getCategory() != null ? asset.getCategory().getName() : "N/A");
            item.put("purchaseDate", asset.getPurchaseDate() != null ? asset.getPurchaseDate().toString() : "N/A");

            String warrantyStatus = "N/A";
            long daysLeft = 0;

            if (asset.getWarrantyEndDate() != null) {
                LocalDate warrantyEnd = asset.getWarrantyEndDate();
                daysLeft = ChronoUnit.DAYS.between(now, warrantyEnd);

                if (daysLeft < 0) {
                    warrantyStatus = "EXPIRED";
                    expiredCount++;
                } else if (daysLeft <= 30) {
                    warrantyStatus = "EXPIRING";
                    expiringCount++;
                } else {
                    warrantyStatus = "ACTIVE";
                    activeCount++;
                }

                item.put("warrantyStart", asset.getPurchaseDate() != null ? asset.getPurchaseDate().toString() : "N/A");
                item.put("warrantyEnd", asset.getWarrantyEndDate().toString());
                item.put("status", warrantyStatus);
                item.put("daysLeft", daysLeft > 0 ? daysLeft + " days" : (daysLeft == 0 ? "Today" : "Expired"));
            } else {
                item.put("warrantyStart", "N/A");
                item.put("warrantyEnd", "N/A");
                item.put("status", "NO WARRANTY");
                item.put("daysLeft", "N/A");
            }

            // Apply status filter
            if (status != null && !status.isEmpty() && !status.equals(item.get("status"))) {
                continue;
            }

            // Apply date filters
            if (dateFrom != null && asset.getWarrantyEndDate() != null) {
                if (asset.getWarrantyEndDate().isBefore(dateFrom)) continue;
            }
            if (dateTo != null && asset.getWarrantyEndDate() != null) {
                if (asset.getWarrantyEndDate().isAfter(dateTo)) continue;
            }

            assetList.add(item);
        }

        response.put("total", assetList.size());
        response.put("activeCount", activeCount);
        response.put("expiringCount", expiringCount);
        response.put("expiredCount", expiredCount);
        response.put("assets", assetList);
        return response;
    }

    // ... (all other report endpoints: transfers, driver-bookings, room-bookings, costs, audit)
    // ... keep all the existing API methods from your ReportViewController

    // ============================================
    // HELPER METHODS
    // ============================================
    private String getUserName(Long userId) {
        if (userId == null) return "N/A";
        return userRepository.findById(userId)
                .map(u -> u.getFullName() != null ? u.getFullName() : u.getUsername())
                .orElse("User #" + userId);
    }

    private String getRoomName(Long roomId) {
        if (roomId == null) return "N/A";
        return roomRepository.findById(roomId)
                .map(Room::getRoomName)
                .orElse("Room #" + roomId);
    }

    private String getDepartmentName(Integer departmentId) {
        if (departmentId == null) return "N/A";
        return departmentRepository.findById(departmentId)
                .map(Department::getName)
                .orElse("Dept #" + departmentId);
    }
}