package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.DriverRequestDTO;
import com.stevecodes.AssetIQPro.entity.*;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.repository.DriverAvailabilityRepository;
import com.stevecodes.AssetIQPro.repository.DriverRatingRepository;
import com.stevecodes.AssetIQPro.repository.DriverRequestRepository;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRequestRepository driverRequestRepository;
    private final DriverAvailabilityRepository availabilityRepository;
    private final DriverRatingRepository ratingRepository;
    private final AppUserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final BaseUrlService baseUrlService;
    private final SLAService slaService;

    private static final int MAX_DAYS = 7;

    // ============================================
    // Available Drivers with Booking Count
    // ============================================

    public List<AppUser> getAvailableDrivers() {
        log.info("Getting all drivers (regardless of availability status)");
        return userRepository.findByRole("DRIVER");
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
        log.info("Getting ALL bookings for driver: {}", driverId);
        return driverRequestRepository.findByDriverId(driverId);
    }

    public List<DriverRequest> getAcceptedRequestsForDriver(Long driverId) {
        log.info("Getting accepted requests for driver: {}", driverId);
        return driverRequestRepository.findByDriverIdAndStatus(driverId, "ACCEPTED");
    }

    // ============================================
    // Driver Request Management
    // ============================================

    @Transactional
    public DriverRequest createDriverRequest(Long userId, String destination, Long driverId,
                                             String reason, String requestedBy, LocalDateTime requestTime,
                                             String tripType) {
        log.info("=== CREATE DRIVER REQUEST START ===");
        log.info("📝 Creating driver request for user: {}, request time: {}", userId, requestTime);
        log.info("📝 Destination: {}, Driver: {}, Reason: {}, Trip Type: {}", destination, driverId, reason, tripType);

        try {
            if (driverId != null && driverId == -1) {
                log.info("📝 This is a CAB request (driverId = -1)");
                return createCabRequest(userId, destination, reason, requestedBy, requestTime);
            }

            if (driverId != null) {
                Optional<DriverAvailability> availability = availabilityRepository.findByDriverId(driverId);
                if (availability.isPresent() && !"AVAILABLE".equals(availability.get().getStatus())) {
                    log.warn("⚠️ Driver {} is currently busy, but booking is still allowed", driverId);
                }
            }

            // Validate request time is not in the past
            if (requestTime.isBefore(LocalDateTime.now())) {
                log.error("❌ Request time is in the past: {}", requestTime);
                throw new IllegalStateException("Cannot book a driver for a past time. Please select a future time.");
            }

            // Validate request time is not more than 5 days in advance
            LocalDateTime maxDate = LocalDateTime.now().plusDays(5);
            if (requestTime.isAfter(maxDate)) {
                log.error("❌ Request time is too far in the future: {}", requestTime);
                throw new IllegalStateException("Bookings are only allowed within 5 days from today. Please select a date within the next 5 days.");
            }
            log.info("✅ Time validation passed");

            DriverRequest request = new DriverRequest();
            request.setUserId(userId);
            request.setDestination(destination);
            request.setDriverId(driverId);
            request.setReason(reason);
            request.setRequestTime(requestTime);
            request.setRequestDate(requestTime.toLocalDate());
            request.setRequestTimeOnly(requestTime.toLocalTime());
            request.setPickupDatetime(requestTime);
            request.setStatus("PENDING");
            request.setRequestedBy(requestedBy);
            request.setTripType(tripType);

            // Set category based on trip type
            if (tripType != null) {
                request.setTripCategory(tripType);
            } else {
                // Auto-calculate if not provided (fallback)
                LocalDate today = LocalDate.now();
                LocalDate requestDate = requestTime.toLocalDate();
                if (requestDate.equals(today)) {
                    request.setTripCategory("TODAY");
                } else if (requestDate.isAfter(today) && requestDate.isBefore(today.plusDays(1))) {
                    request.setTripCategory("TODAY");
                } else if (requestDate.isAfter(today)) {
                    request.setTripCategory("ADVANCE");
                } else {
                    request.setTripCategory("FUTURE");
                }
            }

            // Set expiry time: 30 minutes before the trip for TODAY trips
            if ("TODAY".equals(request.getTripCategory())) {
                request.setExpiryTime(requestTime.minusMinutes(30));
            }

            log.info("💾 Saving driver request");
            DriverRequest saved = driverRequestRepository.save(request);
            log.info("✅ Driver request saved with ID: {}", saved.getRequestId());

            // START SLA TRACKING
            log.info("📊 Starting SLA tracking for driver request: {}", saved.getRequestId());
            try {
                slaService.startSLATracking(saved.getRequestId(), "DRIVER_REQUEST", userId);
                log.info("✅ SLA tracking started for driver request: {}", saved.getRequestId());
            } catch (Exception e) {
                log.error("❌ Failed to start SLA tracking for driver request: {}", e.getMessage(), e);
            }

            // Notify drivers
            if (driverId != null) {
                log.info("📧 Notifying specific driver: {}", driverId);
                notifyDriver(driverId, saved);
            } else {
                log.info("📧 Notifying all available drivers");
                notifyAvailableDrivers(saved);
            }

            // Send acknowledgment to requester using modern HTML email
            sendDriverRequestAcknowledgment(saved);

            // Log audit
            try {
                auditService.logAction("DRIVER_REQUEST_CREATED",
                        "Driver request created by user: " + userId + " to: " + destination + " at: " + requestTime,
                        userId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== CREATE DRIVER REQUEST END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Error creating driver request: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public DriverRequest createCabRequest(Long userId, String destination, String reason, String requestedBy, LocalDateTime requestTime) {
        log.info("=== CREATE CAB REQUEST START ===");
        log.info("📝 Creating CAB request for user: {}", userId);

        try {
            DriverRequest request = new DriverRequest();
            request.setUserId(userId);
            request.setDestination(destination);
            request.setDriverId(-1L);
            request.setReason(reason);
            request.setRequestTime(requestTime != null ? requestTime : LocalDateTime.now());
            request.setStatus("PENDING_ADMIN");
            request.setRequestedBy(requestedBy);

            log.info("💾 Saving CAB request");
            DriverRequest saved = driverRequestRepository.save(request);
            log.info("✅ CAB request saved with ID: {}", saved.getRequestId());

            // Notify admins
            log.info("📧 Notifying admins about CAB request");
            notifyAdminOfCabRequest(saved);

            // Send acknowledgment to requester using modern HTML email
            sendDriverRequestAcknowledgment(saved);

            // Log audit
            try {
                auditService.logAction("CAB_REQUEST_CREATED",
                        "Cab request created by user: " + userId + " to: " + destination,
                        userId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== CREATE CAB REQUEST END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Error creating CAB request: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public DriverRequest approveCabRequest(Long requestId) {
        log.info("=== APPROVE CAB REQUEST START ===");
        log.info("📝 Admin approving cab request: {}", requestId);

        try {
            DriverRequest request = driverRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Cab request not found: {}", requestId);
                        return new RuntimeException("Cab request not found: " + requestId);
                    });
            log.info("✅ Found request with status: {}", request.getStatus());

            if (!"PENDING_ADMIN".equals(request.getStatus()) && !"PENDING".equals(request.getStatus())) {
                log.error("❌ Invalid status: {}", request.getStatus());
                throw new IllegalStateException("Request is not pending approval");
            }

            // For cab requests, we set driverId = -1 (external cab)
            request.setStatus("ACCEPTED");
            request.setDriverId(-1L);
            request.setAcceptedAt(LocalDateTime.now());
            request.setDriverDecisionTime(LocalDateTime.now());

            log.info("💾 Saving approved cab request");
            DriverRequest saved = driverRequestRepository.save(request);
            log.info("✅ Cab request approved");

            // ✅ Send MODERN HTML email to requester
            try {
                String requesterEmail = getUserEmail(request.getUserId());
                String requesterName = getRequesterName(request.getUserId());

                emailService.sendDriverRequestStatusUpdate(
                        requesterEmail,
                        requesterName,
                        String.valueOf(request.getRequestId()),
                        "APPROVED",
                        null, null, null,
                        "Your cab request has been approved. An external cab has been arranged."
                );
                log.info("✅ Modern HTML email sent to requester");
            } catch (Exception e) {
                log.error("❌ Failed to send email: {}", e.getMessage(), e);
            }

            // Log audit
            try {
                auditService.logAction("CAB_REQUEST_APPROVED",
                        "Cab request approved: " + requestId,
                        request.getUserId());
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== APPROVE CAB REQUEST END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Error approving cab request: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // Admin Cab Request Handling
    // ============================================

    public List<DriverRequest> getCabRequests() {
        return driverRequestRepository.findByStatus("PENDING_ADMIN");
    }

    @Transactional
    public DriverRequest assignCabRequest(Long requestId, String notes) {
        log.info("=== ASSIGN CAB REQUEST START ===");
        log.info("📝 Admin assigning cab request: {}", requestId);

        try {
            DriverRequest request = driverRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Cab request not found: {}", requestId);
                        return new RuntimeException("Cab request not found: " + requestId);
                    });
            log.info("✅ Found request with status: {}", request.getStatus());

            if (!"PENDING_ADMIN".equals(request.getStatus())) {
                log.error("❌ Invalid status: {}", request.getStatus());
                throw new IllegalStateException("Request is not pending admin approval");
            }

            request.setStatus("ACCEPTED");
            request.setDriverDecisionTime(LocalDateTime.now());
            request.setDeclineReason(notes);

            log.info("💾 Saving updated request");
            DriverRequest saved = driverRequestRepository.save(request);
            log.info("✅ Cab request assigned");

            // ✅ Send MODERN HTML email to requester
            try {
                String requesterEmail = getUserEmail(request.getUserId());
                String requesterName = getRequesterName(request.getUserId());

                emailService.sendDriverRequestStatusUpdate(
                        requesterEmail,
                        requesterName,
                        String.valueOf(request.getRequestId()),
                        "ASSIGNED",
                        "External Cab",
                        null,
                        null,
                        "Your cab request has been processed. Admin Notes: " + (notes != null ? notes : "N/A")
                );
                log.info("✅ Modern HTML email sent to requester");
            } catch (Exception e) {
                log.error("❌ Failed to send email: {}", e.getMessage(), e);
            }

            // Log audit
            try {
                auditService.logAction("CAB_REQUEST_ASSIGNED",
                        "Cab request assigned: " + requestId,
                        request.getUserId());
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== ASSIGN CAB REQUEST END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Error assigning cab request: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // Driver Request Actions
    // ============================================

    @Transactional
    public DriverRequest acceptRequest(Long requestId, Long driverId) {
        log.info("=== ACCEPT REQUEST START ===");
        log.info("📝 Driver {} accepting request: {}", driverId, requestId);

        try {
            DriverRequest request = driverRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Driver request not found: {}", requestId);
                        return new RuntimeException("Driver request not found: " + requestId);
                    });
            log.info("✅ Found request with status: {}", request.getStatus());

            if (!"PENDING".equals(request.getStatus()) && !"PENDING_ADMIN".equals(request.getStatus())) {
                log.error("❌ Invalid status: {}", request.getStatus());
                throw new IllegalStateException("Request is no longer pending");
            }

            // Get request date and time
            LocalDate requestDate = request.getRequestDate() != null ? request.getRequestDate() : request.getRequestTime().toLocalDate();
            LocalTime requestTime = request.getRequestTimeOnly() != null ? request.getRequestTimeOnly() : request.getRequestTime().toLocalTime();

            // Check for conflicting trips (skip for cab requests with driverId = -1)
            if (driverId != null && driverId != -1L) {
                List<DriverRequest> existingTrips = driverRequestRepository.findByDriverIdAndStatusIn(
                        driverId, List.of("ACCEPTED", "PENDING")
                );

                boolean hasConflict = existingTrips.stream().anyMatch(existing -> {
                    if (existing.getRequestId().equals(requestId)) {
                        return false;
                    }

                    LocalDate existingDate = existing.getRequestDate() != null ? existing.getRequestDate() : existing.getRequestTime().toLocalDate();
                    LocalTime existingTime = existing.getRequestTimeOnly() != null ? existing.getRequestTimeOnly() : existing.getRequestTime().toLocalTime();

                    if (!requestDate.equals(existingDate)) {
                        return false;
                    }

                    LocalTime existingStart = existingTime;
                    LocalTime existingEnd = existingTime.plusHours(2);
                    LocalTime newStart = requestTime;
                    LocalTime newEnd = requestTime.plusHours(2);

                    return !(newEnd.isBefore(existingStart) || newStart.isAfter(existingEnd));
                });

                if (hasConflict) {
                    log.warn("⚠️ Conflict detected for driver {} at time {}", driverId, requestTime);
                    throw new IllegalStateException("Driver already has a booking at this time. Please choose a different time.");
                }
                log.info("✅ No conflicts found");
            }

            request.setStatus("ACCEPTED");
            request.setDriverId(driverId);
            request.setAcceptedAt(LocalDateTime.now());
            request.setDriverDecisionTime(LocalDateTime.now());

            log.info("💾 Saving accepted request");
            DriverRequest saved = driverRequestRepository.save(request);
            log.info("✅ Request accepted");

            // Only update availability to BUSY if the trip is for today and not a cab request
            if (driverId != null && driverId != -1L && requestDate.equals(LocalDate.now())) {
                updateDriverAvailability(driverId, "BUSY");
                log.info("📝 Driver availability set to BUSY for today");
            }

            String driverName = driverId != null && driverId != -1L ? getDriverName(driverId) : "Cab (External)";

            // ✅ Send MODERN HTML emails to requester
            try {
                String requesterEmail = getUserEmail(request.getUserId());
                String requesterName = getRequesterName(request.getUserId());
                String formattedDateTime = requestDate + " at " + requestTime;

                // Send the driver assigned notification
                emailService.sendDriverAssignedNotification(
                        requesterEmail,
                        requesterName,
                        String.valueOf(request.getRequestId()),
                        driverName,
                        null,  // phone - not tracked
                        null,  // vehicle type - not tracked
                        formattedDateTime
                );

                // Also send a status update
                emailService.sendDriverRequestStatusUpdate(
                        requesterEmail,
                        requesterName,
                        String.valueOf(request.getRequestId()),
                        "ASSIGNED",
                        driverName,
                        null,  // phone - not tracked
                        formattedDateTime,
                        "Your driver has been assigned. Please be ready at the pickup location."
                );
                log.info("✅ Modern HTML emails sent to requester");
            } catch (Exception e) {
                log.error("❌ Failed to send email: {}", e.getMessage(), e);
            }

            // Create notification
            try {
                createNotification(
                        request.getUserId(),
                        "DRIVER_REQUEST_ACCEPTED",
                        "Driver Request Accepted",
                        "Your driver request has been accepted by " + driverName + " for " + requestDate + " at " + requestTime,
                        baseUrlService.buildUrl("/bookings/bookings-dashboard")
                );
                log.info("✅ Notification created");
            } catch (Exception e) {
                log.error("❌ Failed to create notification: {}", e.getMessage(), e);
            }

            // Log audit
            try {
                auditService.logAction("DRIVER_REQUEST_ACCEPTED",
                        "Driver request accepted: " + requestId + " by driver: " + driverId + " for " + requestDate,
                        driverId != null ? driverId : request.getUserId());
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== ACCEPT REQUEST END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Error accepting request: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public DriverRequest declineRequest(Long requestId, Long driverId, String reason) {
        log.info("=== DECLINE REQUEST START ===");
        log.info("📝 Driver {} declining request: {} - Reason: {}", driverId, requestId, reason);

        try {
            DriverRequest request = driverRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Driver request not found: {}", requestId);
                        return new RuntimeException("Driver request not found: " + requestId);
                    });
            log.info("✅ Found request with status: {}", request.getStatus());

            if (!"PENDING".equals(request.getStatus()) && !"PENDING_ADMIN".equals(request.getStatus())) {
                log.error("❌ Invalid status: {}", request.getStatus());
                throw new IllegalStateException("Request is no longer pending");
            }

            request.setStatus("DECLINED");
            request.setDeclinedReason(reason);
            request.setDriverDecisionTime(LocalDateTime.now());
            request.setDriverId(driverId);

            log.info("💾 Saving declined request");
            DriverRequest saved = driverRequestRepository.save(request);
            log.info("✅ Request declined");

            // ✅ Send MODERN HTML email to requester
            try {
                String requesterEmail = getUserEmail(request.getUserId());
                String requesterName = getRequesterName(request.getUserId());
                String driverName = driverId != null && driverId != -1L ? getDriverName(driverId) : "Cab (External)";

                emailService.sendDriverRequestStatusUpdate(
                        requesterEmail,
                        requesterName,
                        String.valueOf(request.getRequestId()),
                        "REJECTED",
                        driverName,
                        null,
                        null,
                        "Reason: " + reason + ". Please try requesting another driver or select Cab."
                );
                log.info("✅ Modern HTML email sent to requester");
            } catch (Exception e) {
                log.error("❌ Failed to send email: {}", e.getMessage(), e);
            }

            // Create notification
            try {
                createNotification(
                        request.getUserId(),
                        "DRIVER_REQUEST_DECLINED",
                        "Driver Request Declined",
                        "Your driver request has been declined. Reason: " + reason,
                        baseUrlService.buildUrl("/bookings/bookings-dashboard")
                );
                log.info("✅ Notification created");
            } catch (Exception e) {
                log.error("❌ Failed to create notification: {}", e.getMessage(), e);
            }

            // Log audit
            try {
                auditService.logAction("DRIVER_REQUEST_DECLINED",
                        "Driver request declined: " + requestId + " by driver: " + driverId,
                        driverId != null ? driverId : request.getUserId());
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== DECLINE REQUEST END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Error declining request: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public DriverRequest completeTrip(Long requestId, Long driverId) {
        log.info("=== COMPLETE TRIP START ===");
        log.info("📝 Driver {} completing trip for request: {}", driverId, requestId);

        try {
            DriverRequest request = driverRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Driver request not found: {}", requestId);
                        return new RuntimeException("Driver request not found: " + requestId);
                    });
            log.info("✅ Found request with status: {}", request.getStatus());

            if (!"ACCEPTED".equals(request.getStatus())) {
                log.error("❌ Invalid status for completion: {}", request.getStatus());
                throw new IllegalStateException("Request must be accepted to complete. Current: " + request.getStatus());
            }

            request.setStatus("COMPLETED");
            request.setResponseTime(LocalDateTime.now());

            log.info("💾 Saving completed request");
            DriverRequest saved = driverRequestRepository.save(request);
            log.info("✅ Request completed");

            // Update driver availability
            updateDriverAvailability(driverId, "AVAILABLE");
            log.info("📝 Driver availability set to AVAILABLE");

            // ✅ Complete SLA tracking
            log.info("📊 Completing SLA tracking for driver request: {}", requestId);
            try {
                slaService.completeSLATracking(requestId, "DRIVER_REQUEST");
                log.info("✅ SLA tracking completed for driver request: {}", requestId);
            } catch (Exception e) {
                log.error("❌ Failed to complete SLA tracking for driver request: {}", e.getMessage(), e);
            }

            // Generate rating token
            String ratingToken = UUID.randomUUID().toString();
            request.setNotes(ratingToken);
            driverRequestRepository.save(request);

            String requesterEmail = getUserEmail(request.getUserId());
            String requesterName = getRequesterName(request.getUserId());
            String driverName = getDriverName(driverId);
            String ratingLink = baseUrlService.buildUrl("/bookings/driver-rating/%s?token=%s", requestId, ratingToken);

            // ✅ Send MODERN HTML email with rating link
            try {
                log.info("📧 Sending rating email to: {}", requesterEmail);
                emailService.sendDriverRatingEmail(
                        requesterEmail,
                        requesterName,
                        String.valueOf(request.getRequestId()),
                        driverName,
                        ratingLink
                );
                log.info("✅ Rating email sent to: {}", requesterEmail);
            } catch (Exception e) {
                log.error("❌ Failed to send rating email: {}", e.getMessage(), e);
            }

            // Create notification
            try {
                createNotification(
                        request.getUserId(),
                        "TRIP_COMPLETED",
                        "Trip Completed",
                        "Your trip to " + request.getDestination() + " has been completed. Please rate your driver.",
                        ratingLink
                );
                log.info("✅ Notification created");
            } catch (Exception e) {
                log.error("❌ Failed to create notification: {}", e.getMessage(), e);
            }

            // Log audit
            try {
                auditService.logAction("TRIP_COMPLETED",
                        "Trip completed for request: " + requestId + " by driver: " + driverId,
                        driverId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== COMPLETE TRIP END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Error completing trip: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // Rating Methods using DriverRating entity
    // ============================================

    @Transactional
    public void rateDriver(Long requestId, Long userId, int rating, String feedback) {
        log.info("=== RATE DRIVER START ===");
        log.info("📝 User {} rating driver for request: {} - Rating: {}", userId, requestId, rating);

        try {
            if (ratingRepository.findByRequestId(requestId).isPresent()) {
                log.warn("⚠️ User has already rated this request: {}", requestId);
                throw new IllegalStateException("You have already rated this driver.");
            }

            DriverRequest request = driverRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Driver request not found: {}", requestId);
                        return new RuntimeException("Driver request not found: " + requestId);
                    });

            if (request.getUserId() == null || !request.getUserId().equals(userId)) {
                log.error("❌ User {} not authorized to rate this request", userId);
                throw new IllegalStateException("You are not authorized to rate this trip.");
            }

            DriverRating driverRating = new DriverRating();
            driverRating.setRequestId(requestId);
            driverRating.setDriverId(request.getDriverId());
            driverRating.setUserId(userId);
            driverRating.setRating(rating);
            driverRating.setFeedback(feedback);

            log.info("💾 Saving driver rating");
            ratingRepository.save(driverRating);
            log.info("✅ Rating saved");

            request.setNotes("Rating: " + rating + "/5 | Feedback: " + (feedback != null ? feedback : "N/A"));
            driverRequestRepository.save(request);

            // Log audit
            try {
                auditService.logAction("DRIVER_RATED",
                        "Driver rated for request: " + requestId + " - Rating: " + rating + "/5",
                        userId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== RATE DRIVER END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Error rating driver: {}", e.getMessage(), e);
            throw e;
        }
    }

    public boolean hasUserRated(Long requestId) {
        return ratingRepository.findByRequestId(requestId).isPresent();
    }

    public Double getDriverAverageRating(Long driverId) {
        return ratingRepository.getAverageRatingForDriver(driverId);
    }

    public Long getDriverRatingCount(Long driverId) {
        return ratingRepository.getRatingCountForDriver(driverId);
    }

    // ============================================
    // Recall Request
    // ============================================

    @Transactional
    public void recallRequest(Long requestId) {
        log.info("=== RECALL REQUEST START ===");
        log.info("📝 Recalling driver request: {}", requestId);

        try {
            DriverRequest request = driverRequestRepository.findById(requestId)
                    .orElseThrow(() -> {
                        log.error("❌ Driver request not found: {}", requestId);
                        return new RuntimeException("Driver request not found: " + requestId);
                    });
            log.info("✅ Found request with status: {}", request.getStatus());

            if (!"PENDING".equals(request.getStatus()) && !"PENDING_ADMIN".equals(request.getStatus())) {
                log.error("❌ Cannot recall - request already processed with status: {}", request.getStatus());
                throw new IllegalStateException("Cannot recall - request already processed");
            }

            request.setStatus("RECALLED");
            driverRequestRepository.save(request);
            log.info("✅ Request recalled");

            // Log audit
            try {
                auditService.logAction("DRIVER_REQUEST_RECALLED",
                        "Driver request recalled: " + requestId,
                        request.getUserId());
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== RECALL REQUEST END - SUCCESS ===");

        } catch (Exception e) {
            log.error("❌ Error recalling request: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // Save Request
    // ============================================

    @Transactional
    public void saveRequest(DriverRequest request) {
        log.debug("💾 Saving driver request: {}", request.getRequestId());
        driverRequestRepository.save(request);
    }

    // ============================================
    // Driver Availability Management
    // ============================================

    @Transactional
    public DriverAvailability toggleDriverAvailability(Long driverId) {
        log.info("=== TOGGLE DRIVER AVAILABILITY START ===");
        log.info("📝 Toggling availability for driver: {}", driverId);

        try {
            DriverAvailability availability = availabilityRepository.findByDriverId(driverId)
                    .orElse(new DriverAvailability());

            String newStatus = "AVAILABLE".equals(availability.getStatus()) ? "BUSY" : "AVAILABLE";
            availability.setStatus(newStatus);
            availability.setDriverId(driverId);
            availability.setUserId(driverId);
            availability.setStartTime(LocalDateTime.now());
            availability.setEndTime(LocalDateTime.now().plusHours(8));

            DriverAvailability saved = availabilityRepository.save(availability);
            log.info("✅ Driver availability toggled to: {}", newStatus);

            // Log audit
            try {
                auditService.logAction("DRIVER_AVAILABILITY_TOGGLED",
                        "Driver availability toggled to: " + newStatus + " for driver: " + driverId,
                        driverId);
                log.info("✅ Audit logged");
            } catch (Exception e) {
                log.error("❌ Failed to log audit: {}", e.getMessage(), e);
            }

            log.info("=== TOGGLE DRIVER AVAILABILITY END - SUCCESS ===");
            return saved;

        } catch (Exception e) {
            log.error("❌ Error toggling driver availability: {}", e.getMessage(), e);
            throw e;
        }
    }

    public Optional<DriverAvailability> getDriverAvailability(Long driverId) {
        return availabilityRepository.findByDriverId(driverId);
    }

    @Transactional
    public void updateDriverAvailability(Long driverId, String status) {
        log.debug("📝 Updating driver {} availability to: {}", driverId, status);

        try {
            DriverAvailability availability = availabilityRepository.findByDriverId(driverId)
                    .orElse(new DriverAvailability());

            availability.setDriverId(driverId);
            availability.setUserId(driverId);
            availability.setStatus(status);
            availability.setStartTime(LocalDateTime.now());
            availability.setEndTime(LocalDateTime.now().plusHours(8));

            availabilityRepository.save(availability);
            log.debug("✅ Driver availability updated to: {}", status);

        } catch (Exception e) {
            log.error("❌ Error updating driver availability: {}", e.getMessage(), e);
        }
    }

    public boolean isDriverAvailable(Long driverId, LocalDateTime requestTime) {
        Optional<DriverAvailability> availability = availabilityRepository.findByDriverId(driverId);

        if (availability.isEmpty()) {
            log.debug("ℹ️ No availability record for driver {}, considering them available", driverId);
            return true;
        }

        if (!"AVAILABLE".equals(availability.get().getStatus())) {
            log.debug("ℹ️ Driver {} is not available. Status: {}", driverId, availability.get().getStatus());
            return false;
        }

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
    // Trip Categorization & Expiry
    // ============================================

    public String categorizeTrip(LocalDateTime requestTime) {
        LocalDate today = LocalDate.now();
        LocalDate requestDate = requestTime.toLocalDate();

        if (requestDate.equals(today)) {
            return "TODAY";
        } else if (requestDate.isAfter(today) && requestDate.isBefore(today.plusDays(1))) {
            return "TODAY";
        } else if (requestDate.isAfter(today)) {
            return "ADVANCE";
        } else {
            return "FUTURE";
        }
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireExpiredTrips() {
        log.info("Running scheduled expiry check for driver trips...");
        LocalDateTime now = LocalDateTime.now();

        List<DriverRequest> expiredTrips = driverRequestRepository
                .findByStatusAndIsExpiredFalseAndExpiryTimeBefore("PENDING", now);

        expiredTrips.addAll(driverRequestRepository
                .findByStatusAndIsExpiredFalseAndExpiryTimeBefore("PENDING_ADMIN", now));

        for (DriverRequest trip : expiredTrips) {
            trip.setStatus("EXPIRED");
            trip.setIsExpired(true);
            trip.setDeclineReason("Trip request expired as it was not actioned in time.");
            driverRequestRepository.save(trip);
            log.info("Expired trip {} for user {} to {}", trip.getRequestId(),
                    trip.getUserId(), trip.getDestination());

            // ✅ Send MODERN HTML email for expiry notification
            try {
                String userEmail = getUserEmail(trip.getUserId());
                String userName = getRequesterName(trip.getUserId());
                emailService.sendDriverRequestStatusUpdate(
                        userEmail,
                        userName,
                        String.valueOf(trip.getRequestId()),
                        "EXPIRED",
                        null, null, null,
                        "Your driver request to " + trip.getDestination() +
                                " has expired because it was not actioned in time. Please submit a new request if you still need a driver."
                );
            } catch (Exception e) {
                log.error("Failed to send expiry notification: {}", e.getMessage());
            }
        }
    }

    // ============================================
    // Query Methods    // ============================================

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

    public List<DriverRequest> getPendingRequests() {
        log.info("Getting all pending driver requests including PENDING_ADMIN");
        return driverRequestRepository.findByStatusIn(List.of("PENDING", "PENDING_ADMIN"));
    }

    // ============================================
    // Helper Methods
    // ============================================

    public String getDriverName(Long driverId) {
        return userRepository.findById(driverId)
                .map(AppUser::getFullName)
                .orElse("Driver #" + driverId);
    }

    public String getRequesterName(Long userId) {
        return userRepository.findById(userId)
                .map(AppUser::getFullName)
                .orElse("User #" + userId);
    }

    private void sendDriverRequestAcknowledgment(DriverRequest request) {
        try {
            String requesterEmail = getUserEmail(request.getUserId());
            String requesterName = getRequesterName(request.getUserId());

            emailService.sendDriverRequestAcknowledgment(
                    requesterEmail,
                    requesterName,
                    String.valueOf(request.getRequestId())
            );
            log.info("✅ Acknowledgment email sent to: {}", requesterEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send acknowledgment email: {}", e.getMessage(), e);
        }
    }

    private void notifyAvailableDrivers(DriverRequest request) {
        log.info("📧 Notifying available drivers about request: {}", request.getRequestId());

        try {
            List<AppUser> drivers = userRepository.findByRole("DRIVER");
            String pickupTime = request.getRequestTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            int notifiedCount = 0;
            for (AppUser driver : drivers) {
                Optional<DriverAvailability> availability = availabilityRepository.findByDriverId(driver.getUserId());
                if (availability.isPresent() && !"AVAILABLE".equals(availability.get().getStatus())) {
                    continue;
                }

                try {
                    emailService.sendDriverRequestNotification(
                            driver.getEmail(),
                            request.getRequestedBy() != null ? request.getRequestedBy() : "Employee",
                            request.getDestination() != null ? request.getDestination() : "N/A",
                            "N/A",
                            pickupTime,
                            request.getReason() != null ? request.getReason() : "N/A",
                            String.valueOf(request.getRequestId()),
                            "Driver"
                    );
                    notifiedCount++;
                } catch (Exception e) {
                    log.error("❌ Failed to send email to driver {}: {}", driver.getUserId(), e.getMessage());
                }

                try {
                    createNotification(
                            driver.getUserId(),
                            "DRIVER_REQUEST_NEW",
                            "New Driver Request",
                            "A new driver request has been created by " + request.getRequestedBy() +
                                    " for " + request.getDestination(),
                            "/bookings/driver-dashboard"
                    );
                } catch (Exception e) {
                    log.error("❌ Failed to create notification for driver {}: {}", driver.getUserId(), e.getMessage());
                }
            }

            log.info("✅ Notified {} available drivers", notifiedCount);

        } catch (Exception e) {
            log.error("❌ Failed to notify available drivers: {}", e.getMessage(), e);
        }
    }

    private void notifyDriver(Long driverId, DriverRequest request) {
        log.info("📧 Notifying specific driver: {}", driverId);

        try {
            Optional<AppUser> driver = userRepository.findById(driverId);
            if (driver.isPresent()) {
                String pickupTime = request.getRequestTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

                emailService.sendDriverRequestNotification(
                        driver.get().getEmail(),
                        request.getRequestedBy() != null ? request.getRequestedBy() : "Employee",
                        request.getDestination() != null ? request.getDestination() : "N/A",
                        "N/A",
                        pickupTime,
                        request.getReason() != null ? request.getReason() : "N/A",
                        String.valueOf(request.getRequestId()),
                        "Driver"
                );

                createNotification(
                        driverId,
                        "DRIVER_REQUEST_SPECIFIC",
                        "New Driver Request - You've Been Requested",
                        "You have been requested as a driver by " + request.getRequestedBy() +
                                " for " + request.getDestination(),
                        "/bookings/driver-dashboard"
                );

                log.info("✅ Driver notified: {}", driverId);
            } else {
                log.warn("⚠️ Driver not found with ID: {}", driverId);
            }

        } catch (Exception e) {
            log.error("❌ Failed to notify driver {}: {}", driverId, e.getMessage(), e);
        }
    }

    private void notifyAdminOfCabRequest(DriverRequest request) {
        log.info("📧 Notifying admins about CAB request: {}", request.getRequestId());

        try {
            List<AppUser> admins = userRepository.findByRole("ADMIN");
            String pickupTime = request.getRequestTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            for (AppUser admin : admins) {
                try {
                    emailService.sendDriverRequestNotification(
                            admin.getEmail(),
                            request.getRequestedBy() != null ? request.getRequestedBy() : "Employee",
                            request.getDestination() != null ? request.getDestination() : "N/A",
                            "N/A",
                            pickupTime,
                            request.getReason() != null ? request.getReason() : "N/A",
                            String.valueOf(request.getRequestId()),
                            "Cab"
                    );
                    log.info("✅ Email sent to admin: {}", admin.getEmail());
                } catch (Exception e) {
                    log.error("❌ Failed to send email to admin {}: {}", admin.getUserId(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Failed to notify admins: {}", e.getMessage(), e);
        }
    }

    private void createNotification(Long userId, String type, String title, String message, String link) {
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setLink(link.startsWith("/") ? baseUrlService.buildUrl(link) : link);
            notification.setCreatedAt(LocalDateTime.now());
            notification.setRead(false);
            notificationRepository.save(notification);
            log.debug("✅ Notification created for user: {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to create notification for user {}: {}", userId, e.getMessage(), e);
        }
    }

    private String getUserEmail(Long userId) {
        return userRepository.findById(userId)
                .map(AppUser::getEmail)
                .orElse("user@company.com");
    }

    public List<DriverRequestDTO> getDriverRequestsAsDTOs() {
        log.info("Getting all driver requests as DTOs");
        return driverRequestRepository.findAllByOrderByRequestTimeDesc()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<DriverRequestDTO> getDriverRequestsByDriverIdAsDTOs(Long driverId) {
        log.info("Getting driver requests for driver {} as DTOs", driverId);
        return driverRequestRepository.findByDriverId(driverId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<DriverRequestDTO> getDriverRequestsByUserIdAsDTOs(Long userId) {
        log.info("Getting driver requests for user {} as DTOs", userId);
        return driverRequestRepository.findByUserId(userId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<DriverRequestDTO> getPendingRequestsForDriverAsDTOs(Long driverId) {
        log.info("Getting pending requests for driver {} as DTOs", driverId);
        return driverRequestRepository.findByDriverIdAndStatus(driverId, "PENDING")
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<DriverRequestDTO> getAcceptedRequestsForDriverAsDTOs(Long driverId) {
        log.info("Getting accepted requests for driver {} as DTOs", driverId);
        return driverRequestRepository.findByDriverIdAndStatus(driverId, "ACCEPTED")
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
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

        try {
            RequestSLATracking slaTracking = slaService.getSLAStatus(request.getRequestId(), "DRIVER_REQUEST");
            if (slaTracking != null) {
                dto.setSlaStatus(slaTracking.getStatus());
                dto.setSlaStatusDisplay(slaTracking.getStatusDisplay());
                dto.setSlaPercentage(slaTracking.getPercentageComplete());
            } else {
                dto.setSlaStatus("N/A");
                dto.setSlaStatusDisplay("N/A");
                dto.setSlaPercentage(0.0);
            }
        } catch (Exception e) {
            log.warn("Could not fetch SLA status for driver request {}: {}", request.getRequestId(), e.getMessage());
            dto.setSlaStatus("N/A");
            dto.setSlaStatusDisplay("N/A");
            dto.setSlaPercentage(0.0);
        }

        return dto;
    }
}