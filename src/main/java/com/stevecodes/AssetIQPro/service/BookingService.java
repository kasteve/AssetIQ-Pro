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

    /**
     * Get bookings by user ID - returns List<BookingDTO>
     * This method is used by UserRequestController
     */
    public List<BookingDTO> getBookingsByUserId(Long userId) {
        log.info("Getting bookings for user: {}", userId);
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
    // Driver Requests (Delegated to DriverService)
    // ============================================

    /**
     * @deprecated Use DriverService instead
     */
    @Deprecated
    @Transactional
    public DriverRequestDTO requestDriver(DriverRequestDTO dto) {
        log.warn("DEPRECATED: Use DriverService.createDriverRequest() instead");
        auditService.logAction("DRIVER_REQUESTED",
                "Driver requested by user: " + dto.getUserId(), dto.getUserId());
        return dto;
    }

    /**
     * @deprecated Use DriverService instead
     */
    @Deprecated
    public List<DriverRequestDTO> getDriverRequests(Long driverId) {
        log.warn("DEPRECATED: Use DriverService.getDriverRequestsByDriverId() instead");
        return List.of();
    }

    /**
     * @deprecated Use DriverService instead
     */
    @Deprecated
    @Transactional
    public void acceptDriverRequest(Long requestId) {
        log.warn("DEPRECATED: Use DriverService.acceptRequest() instead");
    }

    /**
     * @deprecated Use DriverService instead
     */
    @Deprecated
    @Transactional
    public void declineDriverRequest(Long requestId, String reason) {
        log.warn("DEPRECATED: Use DriverService.declineRequest() instead");
    }

    // ============================================
    // Resource Requests (Delegated to ResourceRequestService)
    // ============================================

    /**
     * @deprecated Use ResourceRequestService instead
     */
    @Deprecated
    @Transactional
    public ResourceRequestDTO requestResource(ResourceRequestDTO dto) {
        log.warn("DEPRECATED: Use ResourceRequestService.createResourceRequest() instead");
        auditService.logAction("RESOURCE_REQUESTED",
                "Resource requested by user: " + dto.getUserId() + ", type: " + dto.getResourceType(),
                dto.getUserId());
        return dto;
    }

    /**
     * @deprecated Use ResourceRequestService instead
     */
    @Deprecated
    public List<ResourceRequestDTO> getUserResourceRequests(Long userId) {
        log.warn("DEPRECATED: Use ResourceRequestService.getResourceRequestsByUserId() instead");
        return List.of();
    }

    /**
     * @deprecated Use ResourceRequestService instead
     */
    @Deprecated
    @Transactional
    public void approveResourceRequest(Long requestId, String comment) {
        log.warn("DEPRECATED: Use ResourceRequestService.acceptResourceRequest() instead");
    }

    /**
     * @deprecated Use ResourceRequestService instead
     */
    @Deprecated
    @Transactional
    public void rejectResourceRequest(Long requestId, String reason) {
        log.warn("DEPRECATED: Use ResourceRequestService.declineResourceRequest() instead");
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
        // TODO: Implement user email lookup from AppUserService
        return "user@company.com";
    }
}