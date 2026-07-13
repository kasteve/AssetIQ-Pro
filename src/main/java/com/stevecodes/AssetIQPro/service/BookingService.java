package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.BookingDTO;
import com.stevecodes.AssetIQPro.dto.DriverRequestDTO;
import com.stevecodes.AssetIQPro.dto.ResourceRequestDTO;
import com.stevecodes.AssetIQPro.entity.Booking;
import com.stevecodes.AssetIQPro.entity.Booking.BookingStatus;
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
    private final AppUserService appUserService;

    // ============================================
    // Room Bookings
    // ============================================

    @Transactional
    public BookingDTO createRoomBooking(BookingDTO dto) {
        log.info("Creating room booking for user: {}, room: {}", dto.getUserId(), dto.getRoomId());

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
        booking.setPurpose(dto.getPurpose());

        Booking saved = bookingRepository.save(booking);

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