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
import java.util.List;
import java.util.Optional;

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

    // ============================================
    // Driver Request Management
    // ============================================

    @Transactional
    public DriverRequest createDriverRequest(Long userId, String destination, Long driverId,
                                             String reason, String requestedBy) {
        log.info("Creating driver request for user: {}", userId);

        // Check if specific driver is available
        if (driverId != null) {
            Optional<DriverAvailability> availability = availabilityRepository.findByDriverId(driverId);
            if (availability.isPresent() && "BUSY".equals(availability.get().getStatus())) {
                throw new IllegalStateException("Driver is currently busy. Please choose another driver.");
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

        notifyAvailableDrivers(saved);

        auditService.logAction("DRIVER_REQUEST_CREATED",
                "Driver request created by user: " + userId + " to: " + destination,
                userId);

        return saved;
    }

    public boolean isDriverAvailable(Long driverId, LocalDateTime requestTime) {
        // Check if driver already has an accepted request at that time
        List<DriverRequest> existing = driverRequestRepository.findByDriverIdAndStatus(driverId, "ACCEPTED");
        for (DriverRequest req : existing) {
            if (req.getRequestTime().isEqual(requestTime) ||
                    req.getRequestTime().plusHours(2).isAfter(requestTime)) {
                return false;
            }
        }
        return true;
    }

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

    // ============================================
    // Driver Request Actions
    // ============================================

    @Transactional
    public DriverRequest acceptRequest(Long requestId, Long driverId) {
        log.info("Driver {} accepting request: {}", driverId, requestId);

        DriverRequest request = driverRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Driver request not found: " + requestId));

        // Check if already accepted by another driver
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

        // Notify requester via email
        emailService.sendDriverRequestStatusUpdate(
                getUserEmail(request.getUserId()),
                "Driver Request Accepted",
                "Your driver request has been accepted by " + driverName
        );

        // Create notification for requester
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
    public DriverRequest declineRequest(Long requestId, String reason) {
        log.info("Declining driver request: {} - Reason: {}", requestId, reason);

        DriverRequest request = driverRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Driver request not found: " + requestId));

        request.setStatus("DECLINED");
        request.setDeclinedReason(reason);
        request.setDriverDecisionTime(LocalDateTime.now());

        DriverRequest saved = driverRequestRepository.save(request);

        // Notify requester via email
        emailService.sendDriverRequestStatusUpdate(
                getUserEmail(request.getUserId()),
                "Driver Request Declined",
                "Your driver request has been declined. Reason: " + reason
        );

        // Create notification for requester
        createNotification(
                request.getUserId(),
                "DRIVER_REQUEST_DECLINED",
                "Driver Request Declined",
                "Your driver request has been declined. Reason: " + reason,
                "/bookings/bookings-dashboard"
        );

        auditService.logAction("DRIVER_REQUEST_DECLINED",
                "Driver request declined: " + requestId + " - Reason: " + reason,
                request.getDriverId());

        return saved;
    }

    @Transactional
    public void recallRequest(Long requestId) {
        log.info("Recalling driver request: {}", requestId);

        DriverRequest request = driverRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Driver request not found: " + requestId));

        if (!"PENDING".equals(request.getStatus())) {
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
        availability.setEndTime(LocalDateTime.now().plusHours(2));

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
        availability.setEndTime(LocalDateTime.now().plusHours(2));

        availabilityRepository.save(availability);
    }

    // ============================================
    // Helper Methods
    // ============================================

    private void notifyAvailableDrivers(DriverRequest request) {
        List<AppUser> drivers = userRepository.findByRole("DRIVER");

        for (AppUser driver : drivers) {
            String approvalLink = "http://localhost:8091/assetIQ-pro/bookings/driver-dashboard";

            emailService.sendDriverRequestStatusUpdate(
                    driver.getEmail(),
                    "New Driver Request",
                    "A new driver request has been created by " + request.getRequestedBy() +
                            " for " + request.getDestination() + ". Please review and respond."
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

        log.info("Notified {} drivers about request: {}", drivers.size(), request.getRequestId());
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
        return dto;
    }
}