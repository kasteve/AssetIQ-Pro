package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.DriverRequestDTO;
import com.stevecodes.AssetIQPro.entity.DriverAvailability;
import com.stevecodes.AssetIQPro.entity.DriverRequest;
import com.stevecodes.AssetIQPro.repository.DriverAvailabilityRepository;
import com.stevecodes.AssetIQPro.repository.DriverRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRequestRepository driverRequestRepository;
    private final DriverAvailabilityRepository availabilityRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    // ============================================
    // Driver Request Management
    // ============================================

    @Transactional
    public DriverRequest createDriverRequest(Long userId, String destination, Long driverId,
                                             String reason, String requestedBy) {
        log.info("Creating driver request for user: {}", userId);

        DriverRequest request = new DriverRequest();
        request.setUserId(userId);
        request.setDestination(destination);
        request.setDriverId(driverId);
        request.setReason(reason);
        request.setRequestTime(LocalDateTime.now());
        request.setStatus("PENDING");
        request.setRequestedBy(requestedBy);

        DriverRequest saved = driverRequestRepository.save(request);

        // Notify available drivers
        notifyAvailableDrivers(saved);

        auditService.logAction("DRIVER_REQUEST_CREATED",
                "Driver request created by user: " + userId + " to: " + destination,
                userId);

        return saved;
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

        request.setStatus("ACCEPTED");
        request.setDriverId(driverId);
        request.setAcceptedAt(LocalDateTime.now());
        request.setDriverDecisionTime(LocalDateTime.now());

        DriverRequest saved = driverRequestRepository.save(request);

        // Update driver availability
        updateDriverAvailability(driverId, "BUSY");

        // Notify requester
        emailService.sendDriverRequestStatusUpdate(
                getUserEmail(request.getUserId()),
                "Driver Request Accepted",
                "Your driver request has been accepted by driver #" + driverId
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

        auditService.logAction("DRIVER_REQUEST_DECLINED",
                "Driver request declined: " + requestId + " - Reason: " + reason,
                request.getDriverId());

        return saved;
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
        // Get all available drivers and notify them
        // Placeholder implementation
        log.info("Notifying available drivers about request: {}", request.getRequestId());
    }

    private String getUserEmail(Long userId) {
        // TODO: Implement user email lookup
        return "user@company.com";
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