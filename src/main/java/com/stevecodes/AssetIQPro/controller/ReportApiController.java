package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.*;
import com.stevecodes.AssetIQPro.repository.*;
import com.stevecodes.AssetIQPro.service.EmailService;
import com.stevecodes.AssetIQPro.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final ReportService reportService;
    private final EmailService emailService;

    // ============================================
    // 1. ASSETS REPORT (Unified)
    // ============================================
    @GetMapping("/assets")
    public Map<String, Object> getAssetsReport(
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) String status) {

        Map<String, Object> response = new HashMap<>();
        List<Asset> assets = assetRepository.findAll();

        // Apply common filters
        if (category != null) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().getCategoryId().equals(category))
                    .collect(Collectors.toList());
        }

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

        if (status != null && !status.isEmpty()) {
            try {
                Asset.AssetStatus statusEnum = Asset.AssetStatus.valueOf(status.toUpperCase());
                assets = assets.stream()
                        .filter(a -> a.getStatus() == statusEnum)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Invalid status - ignore filter
            }
        }

        // Apply type-specific logic
        List<Map<String, Object>> assetList = new ArrayList<>();
        long activeCount = 0, warningCount = 0, criticalCount = 0;

        switch (type.toLowerCase()) {
            case "warranty":
                assetList = processWarrantyAssets(assets);
                activeCount = assetList.stream().filter(a -> "ACTIVE".equals(a.get("status"))).count();
                warningCount = assetList.stream().filter(a -> "EXPIRING".equals(a.get("status"))).count();
                criticalCount = assetList.stream().filter(a -> "EXPIRED".equals(a.get("status"))).count();
                break;
            case "eol":
                assetList = processEOLAssets(assets);
                activeCount = assetList.stream().filter(a -> "ACTIVE".equals(a.get("status"))).count();
                warningCount = assetList.stream().filter(a -> "EOL_SOON".equals(a.get("status"))).count();
                criticalCount = assetList.stream().filter(a -> "EOL".equals(a.get("status"))).count();
                break;
            case "disposal":
                assetList = processDisposalAssets(assets);
                activeCount = assetList.stream().filter(a -> "ACTIVE".equals(a.get("status"))).count();
                warningCount = assetList.stream().filter(a -> "RETIRED".equals(a.get("status")) || "TRANSFERRED".equals(a.get("status"))).count();
                criticalCount = assetList.stream().filter(a -> "DISPOSED".equals(a.get("status"))).count();
                break;
            case "cost":
                return getCostReport(dateFrom, dateTo, category, null);
            default:
                assetList = processWarrantyAssets(assets);
        }

        response.put("total", assets.size());
        response.put("activeCount", activeCount);
        response.put("warningCount", warningCount);
        response.put("criticalCount", criticalCount);
        response.put("assets", assetList);
        return response;
    }

    private List<Map<String, Object>> processWarrantyAssets(List<Asset> assets) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate now = LocalDate.now();

        for (Asset asset : assets) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", asset.getAssetId());
            item.put("name", asset.getName() != null ? asset.getName() : "N/A");
            item.put("tag", asset.getTag() != null ? asset.getTag() : "N/A");
            item.put("category", asset.getCategory() != null ? asset.getCategory().getName() : "N/A");
            item.put("purchaseDate", asset.getPurchaseDate() != null ? asset.getPurchaseDate().toString() : "N/A");

            String status = "NO_WARRANTY";
            long daysRemaining = 0;

            if (asset.getWarrantyEndDate() != null) {
                daysRemaining = ChronoUnit.DAYS.between(now, asset.getWarrantyEndDate());
                if (daysRemaining < 0) {
                    status = "EXPIRED";
                } else if (daysRemaining <= 30) {
                    status = "EXPIRING";
                } else {
                    status = "ACTIVE";
                }
                item.put("daysRemaining", daysRemaining);
            } else {
                item.put("daysRemaining", "N/A");
            }

            item.put("status", status);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> processEOLAssets(List<Asset> assets) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate now = LocalDate.now();

        for (Asset asset : assets) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", asset.getAssetId());
            item.put("name", asset.getName() != null ? asset.getName() : "N/A");
            item.put("tag", asset.getTag() != null ? asset.getTag() : "N/A");
            item.put("category", asset.getCategory() != null ? asset.getCategory().getName() : "N/A");
            item.put("purchaseDate", asset.getPurchaseDate() != null ? asset.getPurchaseDate().toString() : "N/A");

            String status = "ACTIVE";
            long daysRemaining = 0;

            if (asset.getEolDate() != null) {
                daysRemaining = ChronoUnit.DAYS.between(now, asset.getEolDate());
                if (daysRemaining < 0) {
                    status = "EOL";
                } else if (daysRemaining <= 90) {
                    status = "EOL_SOON";
                } else {
                    status = "ACTIVE";
                }
                item.put("daysRemaining", daysRemaining);
            } else {
                item.put("daysRemaining", "N/A");
            }

            item.put("status", status);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> processDisposalAssets(List<Asset> assets) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Asset asset : assets) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", asset.getAssetId());
            item.put("name", asset.getName() != null ? asset.getName() : "N/A");
            item.put("tag", asset.getTag() != null ? asset.getTag() : "N/A");
            item.put("category", asset.getCategory() != null ? asset.getCategory().getName() : "N/A");
            item.put("purchaseDate", asset.getPurchaseDate() != null ? asset.getPurchaseDate().toString() : "N/A");

            String status = "ACTIVE";
            if (asset.getStatus() != null) {
                switch (asset.getStatus()) {
                    case RETIRED:
                        status = "RETIRED";
                        break;
                    case DISPOSED:
                        status = "DISPOSED";
                        break;
                    case TRANSFERRED:
                        status = "TRANSFERRED";
                        break;
                    case MAINTENANCE:
                        status = "MAINTENANCE";
                        break;
                    case ASSIGNED:
                        status = "ASSIGNED";
                        break;
                    case AVAILABLE:
                    default:
                        status = "ACTIVE";
                        break;
                }
            }
            item.put("status", status);
            item.put("daysRemaining", "N/A");
            result.add(item);
        }
        return result;
    }

    // ============================================
    // 2. TRANSFERS REPORT - FIXED
    // ============================================
    @GetMapping("/transfers")
    public Map<String, Object> getTransfersReport(
            @RequestParam String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer department) {

        Map<String, Object> response = new HashMap<>();
        List<Transfer> transfers = transferRepository.findAll();

        log.info("=== TRANSFERS REPORT ===");
        log.info("Category filter: {}", category);
        log.info("Date from: {}, Date to: {}", dateFrom, dateTo);
        log.info("Status filter: {}", status);
        log.info("Department filter: {}", department);
        log.info("Total transfers before filtering: {}", transfers.size());

        // Filter by category
        if (category != null && !category.isEmpty() && !"ALL".equalsIgnoreCase(category)) {
            transfers = transfers.stream()
                    .filter(t -> {
                        String transferCategory = t.getCategory();
                        log.debug("Transfer {} category: {}", t.getTransferId(), transferCategory);
                        return transferCategory != null && transferCategory.equalsIgnoreCase(category);
                    })
                    .collect(Collectors.toList());
            log.info("After category filter: {} transfers", transfers.size());
        }

        // Apply date filters
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
        log.info("After date filter: {} transfers", transfers.size());

        // Apply status filter
        if (status != null && !status.isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            transfers = transfers.stream()
                    .filter(t -> {
                        String transferStatus = t.getStatus();
                        log.debug("Transfer {} status: {}", t.getTransferId(), transferStatus);
                        return transferStatus != null && transferStatus.equalsIgnoreCase(status);
                    })
                    .collect(Collectors.toList());
            log.info("After status filter: {} transfers", transfers.size());
        }

        // Apply department filter
        if (department != null) {
            transfers = transfers.stream()
                    .filter(t -> (t.getNewDepartmentId() != null && t.getNewDepartmentId().equals(department)) ||
                            (t.getOldDepartmentId() != null && t.getOldDepartmentId().equals(department)))
                    .collect(Collectors.toList());
            log.info("After department filter: {} transfers", transfers.size());
        }

        // Calculate statistics
        long total = transfers.size();
        long pendingCount = transfers.stream()
                .filter(t -> "PENDING".equalsIgnoreCase(t.getStatus()))
                .count();
        long completedCount = transfers.stream()
                .filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()))
                .count();
        long rejectedCount = transfers.stream()
                .filter(t -> "REJECTED".equalsIgnoreCase(t.getStatus()))
                .count();

        List<Map<String, Object>> transferList = transfers.stream()
                .map(t -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", t.getTransferId());
                    item.put("category", t.getCategory() != null ? t.getCategory() : "ASSET");
                    item.put("from", getDepartmentName(t.getOldDepartmentId()));
                    item.put("to", getDepartmentName(t.getNewDepartmentId()));
                    item.put("item", t.getAssetTag() != null ? t.getAssetTag() : "N/A");
                    item.put("date", t.getTransferDate() != null ? t.getTransferDate().toString() : "N/A");
                    item.put("status", t.getStatus() != null ? t.getStatus() : "PENDING");
                    item.put("amount", t.getAmount() != null ? t.getAmount().toString() : "N/A");
                    return item;
                })
                .collect(Collectors.toList());

        response.put("total", total);
        response.put("pendingCount", pendingCount);
        response.put("completedCount", completedCount);
        response.put("rejectedCount", rejectedCount);
        response.put("transfers", transferList);

        log.info("Transfers report result: {} transfers returned", transferList.size());
        return response;
    }

    // ============================================
    // 3. BOOKINGS REPORT (Unified)
    // ============================================
    @GetMapping("/bookings")
    public Map<String, Object> getBookingsReport(
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long specificId,
            @RequestParam(required = false) String status) {

        Map<String, Object> response = new HashMap<>();

        if ("driver".equalsIgnoreCase(type)) {
            return getDriverBookingsReport(dateFrom, dateTo, specificId, status);
        } else if ("room".equalsIgnoreCase(type)) {
            return getRoomBookingsReport(dateFrom, dateTo, specificId, status);
        }

        response.put("total", 0);
        response.put("pendingCount", 0);
        response.put("activeCount", 0);
        response.put("completedCount", 0);
        response.put("bookings", new ArrayList<>());
        return response;
    }

    // ============================================
    // 4. DRIVER BOOKINGS REPORT - FIXED
    // ============================================
    public Map<String, Object> getDriverBookingsReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String status) {

        Map<String, Object> response = new HashMap<>();
        List<DriverRequest> requests = driverRequestRepository.findAll();

        log.info("=== DRIVER BOOKINGS REPORT ===");
        log.info("Date from: {}, Date to: {}", dateFrom, dateTo);
        log.info("Driver ID filter: {}", driverId);
        log.info("Status filter: {}", status);
        log.info("Total requests before filtering: {}", requests.size());

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
        log.info("After date filter: {} requests", requests.size());

        if (driverId != null) {
            requests = requests.stream()
                    .filter(r -> r.getDriverId() != null && r.getDriverId().equals(driverId))
                    .collect(Collectors.toList());
            log.info("After driver filter: {} requests", requests.size());
        }

        if (status != null && !status.isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            requests = requests.stream()
                    .filter(r -> r.getStatus() != null && r.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
            log.info("After status filter: {} requests", requests.size());
        }

        long pendingCount = requests.stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()) || "PENDING_ADMIN".equalsIgnoreCase(r.getStatus()))
                .count();
        long activeCount = requests.stream()
                .filter(r -> "ACCEPTED".equalsIgnoreCase(r.getStatus()))
                .count();
        long completedCount = requests.stream()
                .filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus()))
                .count();

        List<Map<String, Object>> bookingList = requests.stream()
                .map(r -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", r.getRequestId());
                    item.put("requester", r.getRequestedBy() != null ? r.getRequestedBy() : getUserName(r.getUserId()));
                    item.put("driver", getUserName(r.getDriverId()));
                    item.put("destination", r.getDestination() != null ? r.getDestination() : "N/A");
                    item.put("requested", r.getRequestTime() != null ? r.getRequestTime().toString() : "N/A");
                    item.put("status", r.getStatus() != null ? r.getStatus() : "UNKNOWN");
                    return item;
                })
                .collect(Collectors.toList());

        response.put("total", requests.size());
        response.put("pendingCount", pendingCount);
        response.put("activeCount", activeCount);
        response.put("completedCount", completedCount);
        response.put("bookings", bookingList);

        log.info("Driver bookings report: {} bookings returned", bookingList.size());
        return response;
    }

    // ============================================
    // 5. ROOM BOOKINGS REPORT - FIXED
    // ============================================
    public Map<String, Object> getRoomBookingsReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String status) {

        Map<String, Object> response = new HashMap<>();
        List<Booking> bookings = bookingRepository.findAll();

        log.info("=== ROOM BOOKINGS REPORT ===");
        log.info("Date from: {}, Date to: {}", dateFrom, dateTo);
        log.info("Room ID filter: {}", roomId);
        log.info("Status filter: {}", status);
        log.info("Total bookings before filtering: {}", bookings.size());

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
        log.info("After date filter: {} bookings", bookings.size());

        if (roomId != null) {
            bookings = bookings.stream()
                    .filter(b -> b.getRoomId() != null && b.getRoomId().equals(roomId))
                    .collect(Collectors.toList());
            log.info("After room filter: {} bookings", bookings.size());
        }

        if (status != null && !status.isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            try {
                Booking.BookingStatus statusEnum = Booking.BookingStatus.valueOf(status.toUpperCase());
                bookings = bookings.stream()
                        .filter(b -> b.getStatus() == statusEnum)
                        .collect(Collectors.toList());
                log.info("After status filter: {} bookings", bookings.size());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status value: {}", status);
            }
        }

        long pendingCount = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.PENDING)
                .count();
        long activeCount = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.BOOKED || b.getStatus() == Booking.BookingStatus.ACTIVE)
                .count();
        long completedCount = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED)
                .count();

        List<Map<String, Object>> bookingList = bookings.stream()
                .map(b -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", b.getBookingId());
                    item.put("bookedBy", getUserName(b.getUserId()));
                    item.put("room", getRoomName(b.getRoomId()));
                    String timeSlot = "";
                    if (b.getStartTime() != null && b.getEndTime() != null) {
                        timeSlot = b.getStartTime().toLocalTime() + " - " + b.getEndTime().toLocalTime();
                    }
                    item.put("timeSlot", timeSlot);
                    item.put("date", b.getStartTime() != null ? b.getStartTime().toLocalDate().toString() : "N/A");
                    item.put("status", b.getStatus() != null ? b.getStatus().name() : "UNKNOWN");
                    return item;
                })
                .collect(Collectors.toList());

        response.put("total", bookings.size());
        response.put("pendingCount", pendingCount);
        response.put("activeCount", activeCount);
        response.put("completedCount", completedCount);
        response.put("bookings", bookingList);

        log.info("Room bookings report: {} bookings returned", bookingList.size());
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
                    item.put("id", a.getAssetId());
                    item.put("name", a.getName() != null ? a.getName() : "N/A");
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
    // 8. DROPDOWN DATA ENDPOINTS
    // ============================================
    @GetMapping("/drivers")
    public List<Map<String, Object>> getDrivers() {
        return userRepository.findByRole("DRIVER").stream()
                .map(u -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("userId", u.getUserId());
                    item.put("fullName", u.getFullName() != null ? u.getFullName() : u.getUsername());
                    return item;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/rooms")
    public List<Map<String, Object>> getRooms() {
        return roomRepository.findAll().stream()
                .map(r -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("roomId", r.getRoomId());
                    item.put("roomName", r.getRoomName());
                    return item;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/departments")
    public List<Map<String, Object>> getDepartments() {
        return departmentRepository.findAll().stream()
                .map(d -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("departmentId", d.getDepartmentId());
                    item.put("name", d.getName());
                    return item;
                })
                .collect(Collectors.toList());
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

    // ============================================
    // 9. REPORT GENERATION ENDPOINTS - FIXED
    // ============================================

    /**
     * Generate and download a report as PDF
     */
    @GetMapping("/download/{reportType}")
    @PreAuthorize("hasAnyAuthority('VIEW_REPORTS', 'ADMIN')")
    public ResponseEntity<byte[]> downloadReport(
            @PathVariable String reportType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            log.info("📊 Generating report: {} from {} to {}", reportType, startDate, endDate);
            byte[] pdfBytes = reportService.generateExecutiveReport(reportType, startDate, endDate);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);

            String filename = reportType.toLowerCase() + "_report_" +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";
            headers.setContentDispositionFormData("attachment", filename);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);

        } catch (Exception e) {
            log.error("❌ Error generating report: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generate and email a report - FIXED
     */
    @PostMapping("/email/{reportType}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> emailReport(
            @PathVariable String reportType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String email) {

        try {
            log.info("📧 Emailing report: {} to {}", reportType, email);
            log.info("📊 Report period: {} to {}", startDate, endDate);

            // Generate the report
            byte[] pdfBytes = reportService.generateExecutiveReport(reportType, startDate, endDate);
            log.info("✅ Report generated successfully. Size: {} bytes", pdfBytes.length);

            // Build email content
            String subject = reportType + " Report - " + startDate + " to " + endDate;
            String body = String.format("""
                    Dear User,

                    Please find attached the %s report for the period %s to %s.

                    Report Details:
                    - Type: %s
                    - Period: %s to %s
                    - Generated: %s

                    If you have any questions, please contact the IT Asset Management team.

                    Best regards,
                    AssetIQ-Pro Team
                    """,
                    reportType,
                    startDate,
                    endDate,
                    reportType,
                    startDate,
                    endDate,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
            );

            String fileName = reportType.toLowerCase() + "_report_" +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";

            // Send the email with attachment
            emailService.sendEmailWithAttachment(email, subject, body, pdfBytes, fileName);
            log.info("✅ Report email sent successfully to: {}", email);

            return ResponseEntity.ok("Report sent successfully to " + email);

        } catch (Exception e) {
            log.error("❌ Error emailing report: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Failed to send report: " + e.getMessage());
        }
    }

    /**
     * Test email configuration
     */
    @PostMapping("/test-email")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> testEmail(@RequestParam String email) {
        try {
            log.info("📧 Testing email configuration to: {}", email);

            String subject = "AssetIQ-Pro Test Email";
            String body = """
                    Dear User,

                    This is a test email from AssetIQ-Pro.

                    Your email configuration is working correctly.

                    If you received this email, your SMTP settings are properly configured.

                    Best regards,
                    AssetIQ-Pro Team
                    """;

            emailService.sendSimpleEmail(email, subject, body);
            log.info("✅ Test email sent successfully to: {}", email);

            return ResponseEntity.ok("✅ Test email sent successfully to " + email);

        } catch (Exception e) {
            log.error("❌ Failed to send test email: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("❌ Failed to send test email: " + e.getMessage());
        }
    }

    /**
     * Generate report on demand (for scheduled jobs)
     */
    @PostMapping("/generate/{reportType}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> generateReportOnDemand(
            @PathVariable String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            LocalDate now = LocalDate.now();
            if (startDate == null) startDate = now.minusDays(7);
            if (endDate == null) endDate = now;

            byte[] pdfBytes = reportService.generateExecutiveReport(reportType, startDate, endDate);

            // Get recipients from settings
            // List<String> recipients = settingService.getStringList(SystemSettingService.KEY_REPORT_RECIPIENTS);
            // Send email to each recipient

            log.info("✅ Report generated successfully: {} ({} bytes)", reportType, pdfBytes.length);

            return ResponseEntity.ok("Report generated successfully");

        } catch (Exception e) {
            log.error("❌ Error generating report on demand: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Failed to generate report: " + e.getMessage());
        }
    }
}