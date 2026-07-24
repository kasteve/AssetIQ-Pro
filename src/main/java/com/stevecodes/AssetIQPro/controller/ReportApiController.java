package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.*;
import com.stevecodes.AssetIQPro.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

        if (category != null) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().getCategoryId().equals(category))
                    .collect(Collectors.toList());
        }

        LocalDate now = LocalDate.now();
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

            if (status != null && !status.isEmpty() && !status.equals(item.get("status"))) {
                continue;
            }

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

    // ============================================
    // 3. TRANSFERS REPORT
    // ============================================
    @GetMapping("/transfers")
    public Map<String, Object> getTransfersReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {

        Map<String, Object> response = new HashMap<>();
        List<Transfer> transfers = transferRepository.findAll();

        if (dateFrom != null) {
            transfers = transfers.stream()
                    .filter(t -> t.getTransferDate() != null && !t.getTransferDate().isBefore(dateFrom))
                    .collect(Collectors.toList());
        }
        if (dateTo != null) {
            transfers = transfers.stream()
                    .filter(t -> t.getTransferDate() != null && !t.getTransferDate().isAfter(dateTo))
                    .collect(Collectors.toList());
        }

        long pendingCount = transfers.stream()
                .filter(t -> t.getIsFullySigned() != null && !t.getIsFullySigned())
                .count();
        long completedCount = transfers.stream()
                .filter(t -> t.getIsFullySigned() != null && t.getIsFullySigned())
                .count();

        List<Map<String, Object>> transferList = transfers.stream()
                .map(t -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", t.getTransferId());
                    item.put("type", "ASSET");
                    item.put("from", getDepartmentName(t.getOldDepartmentId()));
                    item.put("to", getDepartmentName(t.getNewDepartmentId()));
                    item.put("asset", t.getAssetTag() != null ? t.getAssetTag() : "N/A");
                    item.put("date", t.getTransferDate() != null ? t.getTransferDate().toString() : "N/A");
                    item.put("status", t.getIsFullySigned() != null && t.getIsFullySigned() ? "COMPLETED" : "PENDING");
                    item.put("amount", "N/A");
                    return item;
                })
                .collect(Collectors.toList());

        response.put("total", transfers.size());
        response.put("pendingCount", pendingCount);
        response.put("completedCount", completedCount);
        response.put("rejectedCount", 0);
        response.put("transfers", transferList);
        return response;
    }

    // ============================================
    // 4. DRIVER BOOKINGS REPORT
    // ============================================
    @GetMapping("/driver-bookings")
    public Map<String, Object> getDriverBookingsReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String status) {

        Map<String, Object> response = new HashMap<>();
        List<DriverRequest> requests = driverRequestRepository.findAll();

        if (dateFrom != null) {
            requests = requests.stream()
                    .filter(r -> r.getRequestTime() != null && !r.getRequestTime().toLocalDate().isBefore(dateFrom))
                    .collect(Collectors.toList());
        }
        if (dateTo != null) {
            requests = requests.stream()
                    .filter(r -> r.getRequestTime() != null && !r.getRequestTime().toLocalDate().isAfter(dateTo))
                    .collect(Collectors.toList());
        }
        if (driverId != null) {
            requests = requests.stream()
                    .filter(r -> r.getDriverId() != null && r.getDriverId().equals(driverId))
                    .collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty()) {
            requests = requests.stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        long pendingCount = requests.stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                .count();
        long completedCount = requests.stream()
                .filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus()))
                .count();
        long declinedCount = requests.stream()
                .filter(r -> "DECLINED".equalsIgnoreCase(r.getStatus()))
                .count();

        List<Map<String, Object>> bookingList = requests.stream()
                .map(r -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", r.getRequestId());
                    item.put("requester", r.getRequestedBy() != null ? r.getRequestedBy() : getUserName(r.getUserId()));
                    item.put("driverName", getUserName(r.getDriverId()));
                    item.put("destination", r.getDestination() != null ? r.getDestination() : "N/A");
                    item.put("requestTime", r.getRequestTime() != null ? r.getRequestTime().toString() : "N/A");
                    item.put("status", r.getStatus() != null ? r.getStatus() : "UNKNOWN");
                    item.put("completedAt", r.getResponseTime() != null ? r.getResponseTime().toString() : "N/A");
                    return item;
                })
                .collect(Collectors.toList());

        response.put("total", requests.size());
        response.put("pendingCount", pendingCount);
        response.put("completedCount", completedCount);
        response.put("declinedCount", declinedCount);
        response.put("bookings", bookingList);
        return response;
    }

    // ============================================
    // 5. ROOM BOOKINGS REPORT
    // ============================================
    @GetMapping("/room-bookings")
    public Map<String, Object> getRoomBookingsReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String status) {

        Map<String, Object> response = new HashMap<>();
        List<Booking> bookings = bookingRepository.findAll();

        if (dateFrom != null) {
            bookings = bookings.stream()
                    .filter(b -> b.getStartTime() != null && !b.getStartTime().toLocalDate().isBefore(dateFrom))
                    .collect(Collectors.toList());
        }
        if (dateTo != null) {
            bookings = bookings.stream()
                    .filter(b -> b.getEndTime() != null && !b.getEndTime().toLocalDate().isAfter(dateTo))
                    .collect(Collectors.toList());
        }
        if (roomId != null) {
            bookings = bookings.stream()
                    .filter(b -> b.getRoomId() != null && b.getRoomId().equals(roomId))
                    .collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty()) {
            bookings = bookings.stream()
                    .filter(b -> b.getStatus() != null && b.getStatus().name().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        long pendingCount = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.PENDING)
                .count();
        long completedCount = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED)
                .count();
        long cancelledCount = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.CANCELLED)
                .count();

        List<Map<String, Object>> bookingList = bookings.stream()
                .map(b -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", b.getBookingId());
                    item.put("roomName", getRoomName(b.getRoomId()));
                    item.put("bookedBy", getUserName(b.getUserId()));
                    item.put("startTime", b.getStartTime() != null ? b.getStartTime().toString() : "N/A");
                    item.put("endTime", b.getEndTime() != null ? b.getEndTime().toString() : "N/A");
                    long hours = 0;
                    if (b.getStartTime() != null && b.getEndTime() != null) {
                        hours = ChronoUnit.HOURS.between(b.getStartTime(), b.getEndTime());
                    }
                    item.put("duration", hours + "h");
                    item.put("status", b.getStatus() != null ? b.getStatus().name() : "UNKNOWN");
                    return item;
                })
                .collect(Collectors.toList());

        response.put("total", bookings.size());
        response.put("pendingCount", pendingCount);
        response.put("completedCount", completedCount);
        response.put("cancelledCount", cancelledCount);
        response.put("bookings", bookingList);
        return response;
    }

    // ============================================
    // 6. ASSET COSTS REPORT
    // ============================================
    @GetMapping("/costs")
    public Map<String, Object> getCostReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String currency) {

        Map<String, Object> response = new HashMap<>();
        List<Asset> assets = assetRepository.findAll();

        if (dateFrom != null) {
            assets = assets.stream()
                    .filter(a -> a.getPurchaseDate() != null && !a.getPurchaseDate().isBefore(dateFrom))
                    .collect(Collectors.toList());
        }
        if (dateTo != null) {
            assets = assets.stream()
                    .filter(a -> a.getPurchaseDate() != null && !a.getPurchaseDate().isAfter(dateTo))
                    .collect(Collectors.toList());
        }
        if (category != null) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().getCategoryId().equals(category))
                    .collect(Collectors.toList());
        }

        double totalCost = assets.stream()
                .mapToDouble(a -> a.getPurchaseCost() != null ? a.getPurchaseCost().doubleValue() : 0)
                .sum();

        double avgCost = assets.isEmpty() ? 0 : totalCost / assets.size();

        String currencyStr = currency != null ? currency : "UGX";

        List<Map<String, Object>> assetList = assets.stream()
                .map(a -> {
                    Map<String, Object> item = new HashMap<>();
                    Object assetNameObj = a.getName();
                    item.put("name", assetNameObj != null ? assetNameObj.toString() : "N/A");
                    item.put("tag", a.getTag() != null ? a.getTag() : "N/A");
                    item.put("category", a.getCategory() != null ? a.getCategory().getName() : "N/A");
                    item.put("purchaseDate", a.getPurchaseDate() != null ? a.getPurchaseDate().toString() : "N/A");
                    item.put("cost", a.getPurchaseCost() != null ? a.getPurchaseCost().doubleValue() : 0);
                    item.put("currency", currencyStr);
                    item.put("department", a.getDepartment() != null ? a.getDepartment().getName() : "N/A");
                    return item;
                })
                .collect(Collectors.toList());

        response.put("totalAssets", assets.size());
        response.put("totalCost", totalCost);
        response.put("averageCost", avgCost);
        response.put("currency", currencyStr);
        response.put("assets", assetList);
        return response;
    }

    // ============================================
    // 7. AUDIT LOGS REPORT
    // ============================================
    @GetMapping("/audit")
    public Map<String, Object> getAuditReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String user) {

        Map<String, Object> response = new HashMap<>();
        List<AuditLog> logs = auditLogRepository.findAll();

        if (dateFrom != null) {
            logs = logs.stream()
                    .filter(l -> l.getTimestamp() != null && !l.getTimestamp().toLocalDate().isBefore(dateFrom))
                    .collect(Collectors.toList());
        }
        if (dateTo != null) {
            logs = logs.stream()
                    .filter(l -> l.getTimestamp() != null && !l.getTimestamp().toLocalDate().isAfter(dateTo))
                    .collect(Collectors.toList());
        }
        if (actionType != null && !actionType.isEmpty()) {
            logs = logs.stream()
                    .filter(l -> l.getAction() != null && l.getAction().toUpperCase().contains(actionType.toUpperCase()))
                    .collect(Collectors.toList());
        }
        if (user != null && !user.isEmpty()) {
            logs = logs.stream()
                    .filter(l -> l.getUsername() != null && l.getUsername().toLowerCase().contains(user.toLowerCase()))
                    .collect(Collectors.toList());
        }

        long createCount = logs.stream()
                .filter(l -> l.getAction() != null && l.getAction().toUpperCase().contains("CREATE"))
                .count();
        long updateCount = logs.stream()
                .filter(l -> l.getAction() != null && l.getAction().toUpperCase().contains("UPDATE"))
                .count();
        long deleteCount = logs.stream()
                .filter(l -> l.getAction() != null && l.getAction().toUpperCase().contains("DELETE"))
                .count();

        List<Map<String, Object>> logList = logs.stream()
                .map(l -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("timestamp", l.getTimestamp() != null ? l.getTimestamp().toString() : "N/A");
                    item.put("user", l.getUsername() != null ? l.getUsername() :
                            (l.getUserId() != null ? "User #" + l.getUserId() : "SYSTEM"));
                    item.put("action", l.getAction() != null ? l.getAction() : "N/A");
                    item.put("type", l.getEntityType() != null ? l.getEntityType() : "N/A");
                    item.put("details", l.getDetails() != null ? l.getDetails() : "N/A");
                    item.put("ipAddress", l.getIpAddress() != null ? l.getIpAddress() : "N/A");
                    return item;
                })
                .collect(Collectors.toList());

        response.put("total", logs.size());
        response.put("createCount", createCount);
        response.put("updateCount", updateCount);
        response.put("deleteCount", deleteCount);
        response.put("logs", logList);
        return response;
    }

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