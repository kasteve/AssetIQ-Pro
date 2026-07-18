package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.DriverRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.DriverAvailability;
import com.stevecodes.AssetIQPro.entity.DriverRequest;
import com.stevecodes.AssetIQPro.entity.Notification;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.repository.DriverAvailabilityRepository;
import com.stevecodes.AssetIQPro.repository.DriverRequestRepository;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRequestRepository driverRequestRepository;
    private final DriverAvailabilityRepository availabilityRepository;
    private final AppUserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    private static final int MAX_DAYS = 7;

    // ============================================
    // Available Drivers with Booking Count
    // ============================================

    public List<AppUser> getAvailableDrivers() {
        log.info("Getting available drivers");

        // Get all drivers
        List<AppUser> allDrivers = userRepository.findByRole("DRIVER");

        // Filter only available drivers
        return allDrivers.stream()
                .filter(driver -> {
                    Optional<DriverAvailability> availability = availabilityRepository.findByDriverId(driver.getUserId());
                    return availability.isEmpty() ||
                            "AVAILABLE".equals(availability.get().getStatus());
                })
                .collect(Collectors.toList());
    }

    public List<AppUser> getAllDrivers() {
        return userRepository.findByRole("DRIVER");
    }

    public int getDriverBookingCount(Long driverId, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Getting booking count for driver: {} between {} and {}", driverId, startDate, endDate);

        List<DriverRequest> requests = driverRequestRepository.findByDriverIdAndStatusIn(
                driverId, List.of("ACCEPTED", "COMPLETED")
        );

        return (int) requests.stream()
                .filter(req -> !req.getRequestTime().isBefore(startDate) &&
                        !req.getRequestTime().isAfter(endDate))
                .count();
    }

    public int getDriverBookingCountLast7Days(Long driverId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(MAX_DAYS);
        return getDriverBookingCount(driverId, startDate, now);
    }

    public List<DriverRequest> getDriverBookingsLast7Days(Long driverId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(MAX_DAYS);
        return driverRequestRepository.findByDriverIdAndRequestTimeBetweenAndStatusIn(
                driverId, startDate, now, List.of("ACCEPTED", "COMPLETED")
        );
    }

    // ============================================
    // Driver Request Management
    // ============================================

    @Transactional
    public DriverRequest createDriverRequest(Long userId, String destination, Long driverId,
                                             String reason, String requestedBy) {
        log.info("Creating driver request for user: {}", userId);

        // Check if "Cab" option is selected (driverId = -1 means Cab)
        if (driverId != null && driverId == -1) {
            return createCabRequest(userId, destination, reason, requestedBy);
        }

        // Check if specific driver is available
        if (driverId != null) {
            Optional<DriverAvailability> availability = availabilityRepository.findByDriverId(driverId);
            if (availability.isPresent() && !"AVAILABLE".equals(availability.get().getStatus())) {
                throw new IllegalStateException("Driver is currently not available. Please choose another driver or select Cab.");
            }
        }

        DriverRequest request = new DriverRequest();
        request.setUserId(userId);
        request.setDestination(destination);
        request.setDriverId(driverId);
        request.setReason(reason);
        request.setRequestTime(LocalDateTime.now());
        request.setStatus("PENDING");
        request.setRequestedBy(requestedBy);

        DriverRequest saved = driverRequestRepository.save(request);

        if (driverId != null) {
            // Notify specific driver
            notifyDriver(driverId, saved);
        } else {
            // Notify all available drivers
            notifyAvailableDrivers(saved);
        }

        auditService.logAction("DRIVER_REQUEST_CREATED",
                "Driver request created by user: " + userId + " to: " + destination,
                userId);

        return saved;
    }

    @Transactional
    public DriverRequest createCabRequest(Long userId, String destination, String reason, String requestedBy) {
        log.info("Creating CAB request for user: {}", userId);

        DriverRequest request = new DriverRequest();
        request.setUserId(userId);
        request.setDestination(destination);
        request.setDriverId(-1L); // -1 indicates Cab
        request.setReason(reason);
        request.setRequestTime(LocalDateTime.now());
        request.setStatus("PENDING_ADMIN"); // Goes to admin
        request.setRequestedBy(requestedBy);

        DriverRequest saved = driverRequestRepository.save(request);

        // Notify admin
        notifyAdminOfCabRequest(saved);

        auditService.logAction("CAB_REQUEST_CREATED",
                "Cab request created by user: " + userId + " to: " + destination,
                userId);

        return saved;
    }

    // ============================================
    // Admin Cab Request Handling
    // ============================================

    public List<DriverRequest> getCabRequests() {
        return driverRequestRepository.findByStatus("PENDING_ADMIN");
    }

    @Transactional
    public DriverRequest assignCabRequest(Long requestId, String notes) {
        log.info("Admin assigning cab request: {}", requestId);

        DriverRequest request = driverRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Cab request not found: " + requestId));

        if (!"PENDING_ADMIN".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not pending admin approval");
        }

        request.setStatus("ACCEPTED");
        request.setDriverDecisionTime(LocalDateTime.now());
        request.setDeclineReason(notes); // Store admin notes
        DriverRequest saved = driverRequestRepository.save(request);

        // Notify requester
        emailService.sendSimpleEmail(
                getUserEmail(request.getUserId()),
                "Cab Request Processed",
                "Your cab request to " + request.getDestination() + " has been processed.\n\n" +
                        "Your cab has been arranged and will arrive shortly.\n" +
                        "Admin Notes: " + (notes != null ? notes : "N/A") + "\n\n" +
                        "Thank you for using AssetIQ-Pro."
        );

        auditService.logAction("CAB_REQUEST_ASSIGNED",
                "Cab request assigned: " + requestId,
                request.getUserId());

        return saved;
    }

    // ============================================
    // Driver Request Actions
    // ============================================

    @Transactional
    public DriverRequest acceptRequest(Long requestId, Long driverId) {
        log.info("Driver {} accepting request: {}", driverId, requestId);

        DriverRequest request = driverRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Driver request not found: " + requestId));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is no longer pending");
        }

        // Check if driver is available
        if (!isDriverAvailable(driverId, request.getRequestTime())) {
            throw new IllegalStateException("Driver is not available at the requested time");
        }

        request.setStatus("ACCEPTED");
        request.setDriverId(driverId);
        request.setAcceptedAt(LocalDateTime.now());
        request.setDriverDecisionTime(LocalDateTime.now());

        DriverRequest saved = driverRequestRepository.save(request);

        updateDriverAvailability(driverId, "BUSY");

        String driverName = getDriverName(driverId);

        // Notify requester
        emailService.sendSimpleEmail(
                getUserEmail(request.getUserId()),
                "Driver Request Accepted",
                "Your driver request has been accepted by " + driverName + ".\n\n" +
                        "Destination: " + request.getDestination() + "\n" +
                        "Driver: " + driverName + "\n\n" +
                        "Please be ready at the pickup location."
        );

        createNotification(
                request.getUserId(),
                "DRIVER_REQUEST_ACCEPTED",
                "Driver Request Accepted",
                "Your driver request has been accepted by " + driverName,
                "/bookings/bookings-dashboard"
        );

        auditService.logAction("DRIVER_REQUEST_ACCEPTED",
                "Driver request accepted: " + requestId + " by driver: " + driverId,
                driverId);

        return saved;
    }

    @Transactional
    public DriverRequest declineRequest(Long requestId, Long driverId, String reason) {
        log.info("Driver {} declining request: {} - Reason: {}", driverId, requestId, reason);

        DriverRequest request = driverRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Driver request not found: " + requestId));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is no longer pending");
        }

        request.setStatus("DECLINED");
        request.setDeclinedReason(reason);
        request.setDriverDecisionTime(LocalDateTime.now());
        request.setDriverId(driverId);

        DriverRequest saved = driverRequestRepository.save(request);

        // Notify requester
        emailService.sendSimpleEmail(
                getUserEmail(request.getUserId()),
                "Driver Request Declined",
                "Your driver request has been declined.\n\n" +
                        "Driver: " + getDriverName(driverId) + "\n" +
                        "Reason: " + reason + "\n\n" +
                        "Please try requesting another driver or select Cab."
        );

        createNotification(
                request.getUserId(),
                "DRIVER_REQUEST_DECLINED",
                "Driver Request Declined",
                "Your driver request has been declined. Reason: " + reason,
                "/bookings/bookings-dashboard"
        );

        auditService.logAction("DRIVER_REQUEST_DECLINED",
                "Driver request declined: " + requestId + " by driver: " + driverId,
                driverId);

        return saved;
    }

    @Transactional
    public DriverRequest completeTrip(Long requestId, Long driverId) {
        log.info("Driver {} completing trip for request: {}", driverId, requestId);

        DriverRequest request = driverRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Driver request not found: " + requestId));

        if (!"ACCEPTED".equals(request.getStatus())) {
            throw new IllegalStateException("Request must be accepted to complete. Current: " + request.getStatus());
        }

        request.setStatus("COMPLETED");
        request.setResponseTime(LocalDateTime.now());

        DriverRequest saved = driverRequestRepository.save(request);

        updateDriverAvailability(driverId, "AVAILABLE");

        // Notify requester for rating
        String requesterEmail = getUserEmail(request.getUserId());
        String ratingLink = "http://localhost:8091/assetIQ-pro/bookings/driver-rating/" + requestId;

        emailService.sendSimpleEmail(
                requesterEmail,
                "Trip Completed - Please Rate Your Driver",
                "Your trip to " + request.getDestination() + " has been completed.\n\n" +
                        "Please rate your driver: " + ratingLink + "\n\n" +
                        "Thank you for using AssetIQ-Pro."
        );

        createNotification(
                request.getUserId(),
                "TRIP_COMPLETED",
                "Trip Completed",
                "Your trip to " + request.getDestination() + " has been completed. Please rate your driver.",
                ratingLink
        );

        auditService.logAction("TRIP_COMPLETED",
                "Trip completed for request: " + requestId + " by driver: " + driverId,
                driverId);

        return saved;
    }

    @Transactional
    public void rateDriver(Long requestId, Long userId, int rating, String feedback) {
        log.info("User {} rating driver for request: {} - Rating: {}", userId, requestId, rating);

        DriverRequest request = driverRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Driver request not found: " + requestId));

        if (request.getUserId() == null || !request.getUserId().equals(userId)) {
            throw new IllegalStateException("You are not authorized to rate this trip.");
        }

        request.setNotes("Rating: " + rating + "/5 | Feedback: " + (feedback != null ? feedback : "N/A"));
        driverRequestRepository.save(request);

        auditService.logAction("DRIVER_RATED",
                "Driver rated for request: " + requestId + " - Rating: " + rating + "/5",
                userId);
    }

    @Transactional
    public void recallRequest(Long requestId) {
        log.info("Recalling driver request: {}", requestId);

        DriverRequest request = driverRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Driver request not found: " + requestId));

        if (!"PENDING".equals(request.getStatus()) && !"PENDING_ADMIN".equals(request.getStatus())) {
            throw new IllegalStateException("Cannot recall - request already processed");
        }

        request.setStatus("RECALLED");
        driverRequestRepository.save(request);

        auditService.logAction("DRIVER_REQUEST_RECALLED",
                "Driver request recalled: " + requestId,
                request.getUserId());
    }

    // ============================================
    // Driver Availability Management
    // ============================================

    @Transactional
    public DriverAvailability toggleDriverAvailability(Long driverId) {
        log.info("Toggling availability for driver: {}", driverId);

        DriverAvailability availability = availabilityRepository.findByDriverId(driverId)
                .orElse(new DriverAvailability());

        String newStatus = "AVAILABLE".equals(availability.getStatus()) ? "BUSY" : "AVAILABLE";
        availability.setStatus(newStatus);
        availability.setDriverId(driverId);
        availability.setUserId(driverId);
        availability.setStartTime(LocalDateTime.now());
        availability.setEndTime(LocalDateTime.now().plusHours(8));

        DriverAvailability saved = availabilityRepository.save(availability);

        auditService.logAction("DRIVER_AVAILABILITY_TOGGLED",
                "Driver availability toggled to: " + newStatus + " for driver: " + driverId,
                driverId);

        return saved;
    }

    public Optional<DriverAvailability> getDriverAvailability(Long driverId) {
        return availabilityRepository.findByDriverId(driverId);
    }

    @Transactional
    public void updateDriverAvailability(Long driverId, String status) {
        log.info("Updating driver {} availability to: {}", driverId, status);

        DriverAvailability availability = availabilityRepository.findByDriverId(driverId)
                .orElse(new DriverAvailability());

        availability.setDriverId(driverId);
        availability.setUserId(driverId);
        availability.setStatus(status);
        availability.setStartTime(LocalDateTime.now());
        availability.setEndTime(LocalDateTime.now().plusHours(8));

        availabilityRepository.save(availability);
    }

    public boolean isDriverAvailable(Long driverId, LocalDateTime requestTime) {
        // Check if driver is available in availability table
        Optional<DriverAvailability> availability = availabilityRepository.findByDriverId(driverId);
        if (availability.isPresent() && !"AVAILABLE".equals(availability.get().getStatus())) {
            return false;
        }

        // Check if driver already has an accepted request at that time
        List<DriverRequest> existing = driverRequestRepository.findByDriverIdAndStatusIn(
                driverId, List.of("ACCEPTED", "PENDING")
        );
        for (DriverRequest req : existing) {
            if (req.getRequestTime().isEqual(requestTime) ||
                    req.getRequestTime().plusHours(2).isAfter(requestTime)) {
                return false;
            }
        }
        return true;
    }

    // ============================================
    // Query Methods
    // ============================================

    public List<DriverRequest> getDriverRequests() {
        return driverRequestRepository.findAllByOrderByRequestTimeDesc();
    }

    public List<DriverRequest> getDriverRequestsByDriverId(Long driverId) {
        return driverRequestRepository.findByDriverId(driverId);
    }

    public List<DriverRequest> getPendingRequestsForDriver(Long driverId) {
        return driverRequestRepository.findByDriverIdAndStatus(driverId, "PENDING");
    }

    public List<DriverRequest> getCompletedRequestsForDriver(Long driverId) {
        return driverRequestRepository.findByDriverIdAndStatus(driverId, "COMPLETED");
    }

    public List<DriverRequest> getRequestsByUserId(Long userId) {
        return driverRequestRepository.findByUserId(userId);
    }

    public Optional<DriverRequest> getRequestById(Long requestId) {
        return driverRequestRepository.findById(requestId);
    }

    public List<DriverRequest> getPendingAdminRequests() {
        return driverRequestRepository.findByStatus("PENDING_ADMIN");
    }

    // ============================================
    // Helper Methods
    // ============================================

    private void notifyAvailableDrivers(DriverRequest request) {
        List<AppUser> drivers = userRepository.findByRole("DRIVER");

        for (AppUser driver : drivers) {
            // Check if driver is available
            Optional<DriverAvailability> availability = availabilityRepository.findByDriverId(driver.getUserId());
            if (availability.isPresent() && !"AVAILABLE".equals(availability.get().getStatus())) {
                continue;
            }

            String dashboardLink = "http://localhost:8091/assetIQ-pro/bookings/driver-dashboard";

            emailService.sendSimpleEmail(
                    driver.getEmail(),
                    "New Driver Request - Action Required",
                    "A new driver request has been created.\n\n" +
                            "Requester: " + request.getRequestedBy() + "\n" +
                            "Destination: " + request.getDestination() + "\n" +
                            "Requested At: " + request.getRequestTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n" +
                            "Reason: " + (request.getReason() != null ? request.getReason() : "N/A") + "\n\n" +
                            "Please login to accept or decline: " + dashboardLink
            );

            createNotification(
                    driver.getUserId(),
                    "DRIVER_REQUEST_NEW",
                    "New Driver Request",
                    "A new driver request has been created by " + request.getRequestedBy() +
                            " for " + request.getDestination(),
                    "/bookings/driver-dashboard"
            );
        }

        log.info("Notified available drivers about request: {}", request.getRequestId());
    }

    private void notifyDriver(Long driverId, DriverRequest request) {
        Optional<AppUser> driver = userRepository.findById(driverId);
        if (driver.isPresent()) {
            String dashboardLink = "http://localhost:8091/assetIQ-pro/bookings/driver-dashboard";

            emailService.sendSimpleEmail(
                    driver.get().getEmail(),
                    "New Driver Request - Action Required",
                    "You have been requested as a driver.\n\n" +
                            "Requester: " + request.getRequestedBy() + "\n" +
                            "Destination: " + request.getDestination() + "\n" +
                            "Requested At: " + request.getRequestTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n" +
                            "Reason: " + (request.getReason() != null ? request.getReason() : "N/A") + "\n\n" +
                            "Please login to accept or decline: " + dashboardLink
            );

            createNotification(
                    driverId,
                    "DRIVER_REQUEST_SPECIFIC",
                    "New Driver Request - You've Been Requested",
                    "You have been requested as a driver by " + request.getRequestedBy() +
                            " for " + request.getDestination(),
                    "/bookings/driver-dashboard"
            );
        }
    }

    private void notifyAdminOfCabRequest(DriverRequest request) {
        List<AppUser> admins = userRepository.findByRole("ADMIN");

        for (AppUser admin : admins) {
            String dashboardLink = "http://localhost:8091/assetIQ-pro/bookings/cab-requests";

            emailService.sendSimpleEmail(
                    admin.getEmail(),
                    "New Cab Request - Action Required",
                    "A new cab request has been created.\n\n" +
                            "Requester: " + request.getRequestedBy() + "\n" +
                            "Destination: " + request.getDestination() + "\n" +
                            "Requested At: " + request.getRequestTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n" +
                            "Reason: " + (request.getReason() != null ? request.getReason() : "N/A") + "\n\n" +
                            "Please arrange for an external cab: " + dashboardLink
            );
        }
    }

    private void createNotification(Long userId, String type, String title, String message, String link) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setLink(link);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    private String getUserEmail(Long userId) {
        return userRepository.findById(userId)
                .map(AppUser::getEmail)
                .orElse("user@company.com");
    }

    private String getDriverName(Long driverId) {
        return userRepository.findById(driverId)
                .map(AppUser::getFullName)
                .orElse("Driver #" + driverId);
    }

    public DriverRequestDTO convertToDTO(DriverRequest request) {
        DriverRequestDTO dto = new DriverRequestDTO();
        dto.setRequestId(request.getRequestId());
        dto.setUserId(request.getUserId());
        dto.setDriverId(request.getDriverId());
        dto.setRequestTime(request.getRequestTime());
        dto.setStatus(request.getStatus());
        dto.setDestination(request.getDestination());
        dto.setReason(request.getReason());
        dto.setRequestedBy(request.getRequestedBy());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setDriverDecisionTime(request.getDriverDecisionTime());
        dto.setAcceptedAt(request.getAcceptedAt());
        dto.setDeclineReason(request.getDeclineReason());
        dto.setDeclinedReason(request.getDeclinedReason());
        dto.setNotes(request.getNotes());
        return dto;
    }
}