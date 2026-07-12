package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.BookingDTO;
import com.stevecodes.AssetIQPro.dto.DriverRequestDTO;
import com.stevecodes.AssetIQPro.dto.ResourceRequestDTO;
import com.stevecodes.AssetIQPro.entity.Booking;
import com.stevecodes.AssetIQPro.entity.Booking.BookingStatus;
import com.stevecodes.AssetIQPro.entity.DriverRequest;
import com.stevecodes.AssetIQPro.entity.ResourceRequest;
import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.repository.BookingRepository;
import com.stevecodes.AssetIQPro.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    // ============================================
    // Room Bookings
    // ============================================

    @Transactional
    public BookingDTO createRoomBooking(BookingDTO dto) {
        log.info("Creating room booking for user: {}, room: {}", dto.getUserId(), dto.getRoomId());

        // Check availability
        if (!isRoomAvailable(dto.getRoomId(), dto.getStartTime(), dto.getEndTime())) {
            throw new IllegalStateException("Room is not available at the requested time");
        }

        Booking booking = new Booking();
        booking.setUserId(dto.getUserId());
        booking.setRoomId(dto.getRoomId());
        booking.setStartTime(dto.getStartTime());
        booking.setEndTime(dto.getEndTime());
        booking.setStatus(BookingStatus.BOOKED);
        booking.setCreatedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);

        // Send confirmation email
        emailService.sendBookingConfirmation(
                getEmailForUser(dto.getUserId()),
                dto.getUserName(),
                "Room Booking",
                "Room: " + dto.getRoomName() + " from " + dto.getStartTime() + " to " + dto.getEndTime()
        );

        auditService.logAction("ROOM_BOOKING_CREATED",
                "Room booking created for user: " + dto.getUserId() + ", room: " + dto.getRoomId(),
                dto.getUserId());

        return convertToBookingDTO(saved);
    }

    public List<Room> getAvailableRooms() {
        LocalDateTime now = LocalDateTime.now();
        return roomRepository.findAvailableRooms(now);
    }

    public boolean isRoomAvailable(Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Booking> conflicting = bookingRepository.findActiveBookingsForRoom(roomId, LocalDateTime.now());

        for (Booking booking : conflicting) {
            boolean overlaps = !endTime.isBefore(booking.getStartTime()) &&
                    !startTime.isAfter(booking.getEndTime());
            if (overlaps && booking.getStatus() == BookingStatus.BOOKED) {
                return false;
            }
        }
        return true;
    }

    public List<BookingDTO> getUserBookings(Long userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(this::convertToBookingDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelBooking(Long bookingId) {
        log.info("Cancelling booking: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        auditService.logAction("BOOKING_CANCELLED",
                "Booking cancelled: " + bookingId, booking.getUserId());
    }

    // ============================================
    // Driver Requests
    // ============================================

    @Transactional
    public DriverRequestDTO requestDriver(DriverRequestDTO dto) {
        log.info("Requesting driver for user: {}", dto.getUserId());

        // Create driver request
        DriverRequest request = new DriverRequest();
        request.setUserId(dto.getUserId());
        request.setDriverId(dto.getDriverId());
        request.setRequestTime(LocalDateTime.now());
        request.setStatus("PENDING");
        request.setDestination(dto.getDestination());
        request.setReason(dto.getReason());
        request.setRequestedBy(dto.getRequestedBy());

        // Save and return
        // Placeholder - implement full driver request logic

        auditService.logAction("DRIVER_REQUESTED",
                "Driver requested by user: " + dto.getUserId(), dto.getUserId());

        return dto;
    }

    public List<DriverRequestDTO> getDriverRequests(Long driverId) {
        // Placeholder
        return List.of();
    }

    @Transactional
    public void acceptDriverRequest(Long requestId) {
        log.info("Accepting driver request: {}", requestId);
        // Placeholder
    }

    @Transactional
    public void declineDriverRequest(Long requestId, String reason) {
        log.info("Declining driver request: {} - Reason: {}", requestId, reason);
        // Placeholder
    }

    // ============================================
    // Resource Requests (Stationery, Apparel, etc.)
    // ============================================

    @Transactional
    public ResourceRequestDTO requestResource(ResourceRequestDTO dto) {
        log.info("Requesting resource for user: {}", dto.getUserId());

        // Create resource request
        ResourceRequest request = new ResourceRequest();
        request.setUserId(dto.getUserId());
        request.setDescription(dto.getDescription());
        request.setRequestTime(LocalDateTime.now());
        request.setResourceType(dto.getResourceType());
        request.setStatus("PENDING");
        request.setRequestedBy(dto.getRequestedBy());

        // Save and return
        // Placeholder - implement full resource request logic

        auditService.logAction("RESOURCE_REQUESTED",
                "Resource requested by user: " + dto.getUserId() + ", type: " + dto.getResourceType(),
                dto.getUserId());

        return dto;
    }

    public List<ResourceRequestDTO> getUserResourceRequests(Long userId) {
        // Placeholder
        return List.of();
    }

    @Transactional
    public void approveResourceRequest(Long requestId, String comment) {
        log.info("Approving resource request: {} - Comment: {}", requestId, comment);
        // Placeholder
    }

    @Transactional
    public void rejectResourceRequest(Long requestId, String reason) {
        log.info("Rejecting resource request: {} - Reason: {}", requestId, reason);
        // Placeholder
    }

    // ============================================
    // Helper Methods
    // ============================================

    private BookingDTO convertToBookingDTO(Booking booking) {
        BookingDTO dto = new BookingDTO();
        dto.setBookingId(booking.getBookingId());
        dto.setUserId(booking.getUserId());
        dto.setRoomId(booking.getRoomId());
        dto.setStartTime(booking.getStartTime());
        dto.setEndTime(booking.getEndTime());
        dto.setStatus(booking.getStatus().name());
        dto.setCreatedAt(booking.getCreatedAt());

        // Get room name
        roomRepository.findById(booking.getRoomId())
                .ifPresent(room -> {
                    dto.setRoomName(room.getRoomName());
                    dto.setRoomType(room.getRoomType());
                });

        return dto;
    }

    private String getEmailForUser(Long userId) {
        // Get email from user service
        return "user@company.com"; // Placeholder
    }
}
