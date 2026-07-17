package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.BookingDTO;
import com.stevecodes.AssetIQPro.entity.Booking;
import com.stevecodes.AssetIQPro.entity.Booking.BookingStatus;
import com.stevecodes.AssetIQPro.entity.Notification;
import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.repository.BookingRepository;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import com.stevecodes.AssetIQPro.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final AppUserService appUserService;
    private final NotificationRepository notificationRepository;

    // ============================================
    // Room Bookings
    // ============================================

    @Transactional
    public BookingDTO createRoomBooking(BookingDTO dto) {
        log.info("Creating room booking for user: {}, room: {}", dto.getUserId(), dto.getRoomId());

        // Check if room is available
        String booker = getRoomBooker(dto.getRoomId(), dto.getStartTime(), dto.getEndTime());
        if (booker != null) {
            throw new IllegalStateException("Room is already booked by " + booker + " at the requested time");
        }

        Booking booking = new Booking();
        booking.setUserId(dto.getUserId());
        booking.setRoomId(dto.getRoomId());
        booking.setStartTime(dto.getStartTime());
        booking.setEndTime(dto.getEndTime());
        booking.setStatus(BookingStatus.BOOKED);
        booking.setCreatedAt(LocalDateTime.now());
        booking.setPurpose(dto.getPurpose());

        Booking saved = bookingRepository.save(booking);

        Room room = roomRepository.findById(dto.getRoomId()).orElse(null);
        String roomName = room != null ? room.getRoomName() : "Room #" + dto.getRoomId();

        emailService.sendBookingConfirmation(
                getEmailForUser(dto.getUserId()),
                dto.getUserName(),
                "Room Booking",
                "Room: " + roomName + " from " + dto.getStartTime() + " to " + dto.getEndTime()
        );

        auditService.logAction("ROOM_BOOKING_CREATED",
                "Room booking created for user: " + dto.getUserId() + ", room: " + dto.getRoomId(),
                dto.getUserId());

        return convertToBookingDTO(saved);
    }

    public boolean isRoomAvailable(Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Booking> bookings = bookingRepository.findByRoomId(roomId);
        for (Booking booking : bookings) {
            if (booking.getStatus() == BookingStatus.BOOKED ||
                    booking.getStatus() == BookingStatus.CONFIRMED) {
                boolean overlaps = !endTime.isBefore(booking.getStartTime()) &&
                        !startTime.isAfter(booking.getEndTime());
                if (overlaps) {
                    return false;
                }
            }
        }
        return true;
    }

    public String getRoomBooker(Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Booking> bookings = bookingRepository.findByRoomId(roomId);
        for (Booking booking : bookings) {
            if (booking.getStatus() == BookingStatus.BOOKED &&
                    !endTime.isBefore(booking.getStartTime()) &&
                    !startTime.isAfter(booking.getEndTime())) {
                // Get user name
                return appUserService.getUserById(booking.getUserId())
                        .map(user -> user.getFullName())
                        .orElse("User #" + booking.getUserId());
            }
        }
        return null;
    }

    @Transactional
    public void requestSlot(Long bookingId, Long requesterId) {
        log.info("Requesting slot for booking: {} by user: {}", bookingId, requesterId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        String requesterName = appUserService.getUserById(requesterId)
                .map(user -> user.getFullName())
                .orElse("User #" + requesterId);

        Room room = roomRepository.findById(booking.getRoomId()).orElse(null);
        String roomName = room != null ? room.getRoomName() : "Room #" + booking.getRoomId();

        String timeSlot = booking.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                " to " + booking.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));

        // Notify current booker via email
        String currentBookerEmail = getEmailForUser(booking.getUserId());
        emailService.sendRoomSlotRequest(
                currentBookerEmail,
                requesterName,
                roomName,
                timeSlot
        );

        // Create notification for current booker
        createNotification(
                booking.getUserId(),
                "ROOM_SLOT_REQUEST",
                "Room Slot Request",
                requesterName + " has requested to use " + roomName + " during your booking (" + timeSlot + ")",
                "/bookings/bookings-dashboard"
        );

        // Create notification for requester
        createNotification(
                requesterId,
                "ROOM_SLOT_REQUESTED",
                "Room Slot Requested",
                "You have requested to use " + roomName + " from " + timeSlot,
                "/bookings/bookings-dashboard"
        );

        auditService.logAction("ROOM_SLOT_REQUESTED",
                "Room slot requested for booking: " + bookingId + " by user: " + requesterId,
                requesterId);
    }

    @Transactional
    public void approveSlotRequest(Long bookingId, Long approverId) {
        log.info("Approving slot request for booking: {} by user: {}", bookingId, approverId);

        // Logic to swap or share the slot
        // Implementation depends on your business rules

        auditService.logAction("ROOM_SLOT_APPROVED",
                "Room slot request approved for booking: " + bookingId + " by user: " + approverId,
                approverId);
    }

    public List<Room> getAvailableRooms() {
        LocalDateTime now = LocalDateTime.now();
        return roomRepository.findAvailableRooms(now);
    }

    public List<BookingDTO> getBookingsWithUserNames(Long userId) {
        List<Booking> bookings = bookingRepository.findByUserId(userId);
        return bookings.stream().map(booking -> {
            BookingDTO dto = new BookingDTO();
            dto.setBookingId(booking.getBookingId());
            dto.setRoomId(booking.getRoomId());
            dto.setStartTime(booking.getStartTime());
            dto.setEndTime(booking.getEndTime());
            dto.setStatus(booking.getStatus().name());
            dto.setPurpose(booking.getPurpose());
            dto.setUserId(booking.getUserId());

            roomRepository.findById(booking.getRoomId()).ifPresent(room -> {
                dto.setRoomName(room.getRoomName());
                dto.setRoomType(room.getRoomType());
            });

            appUserService.getUserById(booking.getUserId()).ifPresent(user -> {
                dto.setBookedBy(user.getFullName());
                dto.setBookedByUsername(user.getUsername());
            });

            return dto;
        }).collect(Collectors.toList());
    }

    public List<BookingDTO> getUserBookings(Long userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(this::convertToBookingDTO)
                .collect(Collectors.toList());
    }

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

    @Transactional
    public void recallRoomBooking(Long bookingId) {
        log.info("Recalling room booking: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw new IllegalStateException("Cannot recall - booking already processed");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        auditService.logAction("ROOM_BOOKING_RECALLED",
                "Room booking recalled: " + bookingId,
                booking.getUserId());
    }

    // ============================================
    // Helper Methods
    // ============================================

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

    private BookingDTO convertToBookingDTO(Booking booking) {
        BookingDTO dto = new BookingDTO();
        dto.setBookingId(booking.getBookingId());
        dto.setUserId(booking.getUserId());
        dto.setRoomId(booking.getRoomId());
        dto.setStartTime(booking.getStartTime());
        dto.setEndTime(booking.getEndTime());
        dto.setStatus(booking.getStatus().name());
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setPurpose(booking.getPurpose());

        roomRepository.findById(booking.getRoomId())
                .ifPresent(room -> {
                    dto.setRoomName(room.getRoomName());
                    dto.setRoomType(room.getRoomType());
                });

        return dto;
    }

    private String getEmailForUser(Long userId) {
        return appUserService.getUserById(userId)
                .map(user -> user.getEmail())
                .orElse("user@company.com");
    }
}