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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private final BaseUrlService baseUrlService;  // ✅ ADDED

    private static final int MAX_BOOKING_DAYS = 7;

    // ============================================
    // Room Bookings
    // ============================================

    @Transactional
    public BookingDTO createRoomBooking(BookingDTO dto) {
        log.info("=== CREATE ROOM BOOKING SERVICE ===");
        log.info("userId: {}, roomId: {}, startTime: {}, endTime: {}",
                dto.getUserId(), dto.getRoomId(), dto.getStartTime(), dto.getEndTime());

        Room room = roomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found: " + dto.getRoomId()));
        log.info("Room found: {}, type: {}", room.getRoomName(), room.getRoomType());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime maxDate = now.plusDays(MAX_BOOKING_DAYS);

        if (dto.getStartTime().isBefore(now)) {
            throw new IllegalStateException("Cannot book a room in the past.");
        }

        if (dto.getStartTime().isAfter(maxDate)) {
            throw new IllegalStateException("Bookings are only allowed within " + MAX_BOOKING_DAYS +
                    " days from today. Please select a date within the next " + MAX_BOOKING_DAYS + " days.");
        }

        if (room.getStatus() != Room.RoomStatus.AVAILABLE) {
            throw new IllegalStateException("Room is not available for booking.");
        }

        String booker = getRoomBooker(dto.getRoomId(), dto.getStartTime(), dto.getEndTime());
        if (booker != null) {
            throw new IllegalStateException("Room is already booked by " + booker +
                    " at the requested time. Please choose a different time.");
        }

        boolean isServerRoom = "Server Room".equalsIgnoreCase(room.getRoomType()) ||
                "Server".equalsIgnoreCase(room.getRoomType()) ||
                (room.getRoomName() != null && room.getRoomName().toLowerCase().contains("server"));

        Booking booking = new Booking();
        booking.setUserId(dto.getUserId());
        booking.setRoomId(dto.getRoomId());
        booking.setStartTime(dto.getStartTime());
        booking.setEndTime(dto.getEndTime());
        booking.setPurpose(dto.getPurpose());
        booking.setCreatedAt(LocalDateTime.now());

        if (isServerRoom) {
            booking.setStatus(BookingStatus.PENDING);
            log.info("Server room booking requires infrastructure approval");
        } else {
            booking.setStatus(BookingStatus.BOOKED);
            log.info("Regular room booking - status set to BOOKED");
        }

        Booking saved = bookingRepository.save(booking);
        log.info("✅ Booking saved with ID: {}, Status: {}", saved.getBookingId(), saved.getStatus());

        if (isServerRoom) {
            notifyInfrastructureTeam(saved, room);
        } else {
            sendBookingConfirmation(saved, room);
        }

        auditService.logAction("ROOM_BOOKING_CREATED",
                "Room booking created for user: " + dto.getUserId() + ", room: " + dto.getRoomId(),
                dto.getUserId());

        return convertToBookingDTO(saved);
    }

    public String getRoomBooker(Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("Checking room availability for room: {}, start: {}, end: {}", roomId, startTime, endTime);

        List<Booking> bookings = bookingRepository.findByRoomId(roomId);
        log.info("Found {} bookings for room {}", bookings.size(), roomId);

        for (Booking booking : bookings) {
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                continue;
            }

            if (booking.getStatus() == BookingStatus.PENDING) {
                log.info("Skipping PENDING booking {} - does not block availability", booking.getBookingId());
                continue;
            }

            if (booking.getStatus() == BookingStatus.BOOKED ||
                    booking.getStatus() == BookingStatus.CONFIRMED ||
                    booking.getStatus() == BookingStatus.ACTIVE) {

                boolean overlaps = !endTime.isBefore(booking.getStartTime()) &&
                        !startTime.isAfter(booking.getEndTime());

                if (overlaps) {
                    String bookerName = appUserService.getUserById(booking.getUserId())
                            .map(user -> user.getFullName())
                            .orElse("User #" + booking.getUserId());
                    log.info("Conflict found: Room is booked by {} from {} to {}",
                            bookerName, booking.getStartTime(), booking.getEndTime());
                    return bookerName;
                }
            }
        }

        log.info("No conflict found - room is available");
        return null;
    }

    public List<BookingDTO> getBookingsForRoom(Long roomId) {
        log.info("Getting all bookings for room: {}", roomId);
        List<Booking> bookings = bookingRepository.findByRoomId(roomId);
        return bookings.stream()
                .map(this::convertToBookingDTO)
                .collect(Collectors.toList());
    }

    public List<BookingDTO> getBookingsForRoomInDateRange(Long roomId, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Getting bookings for room: {} between {} and {}", roomId, startDate, endDate);
        List<Booking> bookings = bookingRepository.findBookingsForRoomInDateRange(roomId, startDate, endDate);
        return bookings.stream()
                .map(this::convertToBookingDTO)
                .collect(Collectors.toList());
    }

    // ============================================
    // Server Room - Infrastructure Approval (with Comments)
    // ============================================

    @Transactional
    public BookingDTO approveServerRoom(Long bookingId, Long approverId, String comment) {
        log.info("Infrastructure team approving server room booking: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Booking is not pending approval. Current status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.BOOKED);
        booking.setApprovedBy(approverId);
        booking.setApprovedAt(LocalDateTime.now());
        booking.setInfraComment(comment != null ? comment : "Approved by Infrastructure");
        booking.setNotes("Approved by Infrastructure: " + (comment != null ? comment : ""));

        Booking saved = bookingRepository.save(booking);

        Room room = roomRepository.findById(booking.getRoomId()).orElse(null);
        String roomName = room != null ? room.getRoomName() : "Server Room";

        String requesterEmail = getEmailForUser(booking.getUserId());
        emailService.sendSimpleEmail(
                requesterEmail,
                "Server Room Booking Approved",
                "Your server room booking for " + roomName + " has been approved.\n\n" +
                        "Booking Details:\n" +
                        "Date: " + booking.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "\n" +
                        "Time: " + booking.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) +
                        " - " + booking.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) + "\n" +
                        "Purpose: " + (booking.getPurpose() != null ? booking.getPurpose() : "N/A") + "\n" +
                        "Infra Comment: " + (comment != null ? comment : "N/A") + "\n\n" +
                        "After using the server room, you will receive a link to sign out."
        );

        createNotification(
                booking.getUserId(),
                "SERVER_ROOM_APPROVED",
                "Server Room Approved",
                "Your server room booking for " + roomName + " has been approved.",
                baseUrlService.buildUrl("/bookings/bookings-dashboard")  // ✅ DYNAMIC
        );

        auditService.logAction("SERVER_ROOM_APPROVED",
                "Server room booking approved: " + bookingId + " by: " + approverId,
                approverId);

        return convertToBookingDTO(saved);
    }

    @Transactional
    public BookingDTO declineServerRoom(Long bookingId, Long approverId, String reason) {
        log.info("Infrastructure team declining server room booking: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Booking is not pending approval. Current status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setDeclinedBy(approverId);
        booking.setDeclinedAt(LocalDateTime.now());
        booking.setDeclinedReason(reason);
        booking.setInfraComment("Declined: " + reason);
        booking.setNotes("Declined by Infrastructure: " + reason);

        Booking saved = bookingRepository.save(booking);

        Room room = roomRepository.findById(booking.getRoomId()).orElse(null);
        String roomName = room != null ? room.getRoomName() : "Server Room";

        String requesterEmail = getEmailForUser(booking.getUserId());
        emailService.sendSimpleEmail(
                requesterEmail,
                "Server Room Booking Declined",
                "Your server room booking for " + roomName + " has been declined.\n\n" +
                        "Reason: " + reason + "\n\n" +
                        "Please contact Infrastructure Team for more information."
        );

        createNotification(
                booking.getUserId(),
                "SERVER_ROOM_DECLINED",
                "Server Room Declined",
                "Your server room booking for " + roomName + " has been declined. Reason: " + reason,
                baseUrlService.buildUrl("/bookings/bookings-dashboard")  // ✅ DYNAMIC
        );

        auditService.logAction("SERVER_ROOM_DECLINED",
                "Server room booking declined: " + bookingId + " by: " + approverId,
                approverId);

        return convertToBookingDTO(saved);
    }

    @Transactional
    public String generateServerRoomSignOutLink(Long bookingId, Long approverId, String infraComment) {
        log.info("Generating sign-out link for server room booking: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw new IllegalStateException("Booking must be in BOOKED status to generate sign-out link. Current: " + booking.getStatus());
        }

        String token = UUID.randomUUID().toString();
        booking.setSignoutToken(token);
        booking.setSignoutTokenExpiry(LocalDateTime.now().plusHours(24));
        booking.setStatus(BookingStatus.ACTIVE);
        if (infraComment != null && !infraComment.isEmpty()) {
            booking.setInfraComment(infraComment);
        }
        booking.setNotes("Sign-out token generated by: " + approverId + " at " + LocalDateTime.now() +
                (infraComment != null ? " | Comment: " + infraComment : ""));
        bookingRepository.save(booking);

        // ✅ DYNAMIC URL
        String signOutLink = baseUrlService.buildUrl("/bookings/server-room/sign-out?token=%s", token);

        String requesterEmail = getEmailForUser(booking.getUserId());
        String roomName = roomRepository.findById(booking.getRoomId())
                .map(Room::getRoomName)
                .orElse("Server Room");

        emailService.sendSimpleEmail(
                requesterEmail,
                "Server Room - Please Sign Out",
                "Dear User,\n\n" +
                        "Your server room usage session is complete. Please sign out by clicking the link below:\n\n" +
                        signOutLink + "\n\n" +
                        "Room: " + roomName + "\n" +
                        "Booking Date: " + booking.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                        " - " + booking.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) + "\n\n" +
                        "If you did not use the server room, please contact Infrastructure Team immediately.\n\n" +
                        "Thank you,\nAssetIQ-Pro Team"
        );

        createNotification(
                booking.getUserId(),
                "SERVER_ROOM_SIGNOUT",
                "Server Room - Please Sign Out",
                "Please sign out of the server room using the link sent to your email.",
                signOutLink
        );

        auditService.logAction("SERVER_ROOM_SIGNOUT_GENERATED",
                "Server room sign-out link generated for booking: " + bookingId + " by: " + approverId,
                approverId);

        return signOutLink;
    }

    @Transactional
    public void completeServerRoomSignOut(String token, String signature, String requesterComment) {
        log.info("Completing server room sign-out with token: {}", token);

        Booking booking = bookingRepository.findBySignoutToken(token);
        if (booking == null) {
            throw new RuntimeException("Invalid sign-out token");
        }

        if (booking.getSignoutTokenExpiry() != null &&
                booking.getSignoutTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Sign-out token has expired");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSignature(signature);
        booking.setSignedOutAt(LocalDateTime.now());
        booking.setSignoutToken(null);
        booking.setSignoutTokenExpiry(null);
        if (requesterComment != null && !requesterComment.isEmpty()) {
            booking.setRequesterComment(requesterComment);
        }
        booking.setNotes("Signed out on: " + LocalDateTime.now() +
                (requesterComment != null ? " | Comment: " + requesterComment : ""));
        bookingRepository.save(booking);

        auditService.logAction("SERVER_ROOM_SIGNOUT_COMPLETED",
                "Server room sign-out completed for booking: " + booking.getBookingId(),
                booking.getUserId());
    }

    public Booking findBySignoutToken(String token) {
        log.info("Finding booking by signout token: {}", token);
        return bookingRepository.findBySignoutToken(token);
    }

    // ============================================
    // Room Details Modal - Get all bookings for a room
    // ============================================

    public Room getRoomWithBookings(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));
    }

    public List<BookingDTO> getRoomBookingsWithUserNames(Long roomId) {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> bookings = bookingRepository.findByRoomId(roomId);

        return bookings.stream().map(booking -> {
            BookingDTO dto = convertToBookingDTO(booking);
            appUserService.getUserById(booking.getUserId()).ifPresent(user -> {
                dto.setBookedBy(user.getFullName());
                dto.setBookedByUsername(user.getUsername());
            });

            // Check if booking is currently active (IN USE)
            boolean isCurrentlyActive = booking.getStartTime().isBefore(now) && booking.getEndTime().isAfter(now);
            dto.setCurrentlyActive(isCurrentlyActive);
            dto.setActive(true);

            return dto;
        }).collect(Collectors.toList());
    }

    // ============================================
    // Slot Request (Request a booked slot)
    // ============================================

    @Transactional
    public void requestSlot(Long bookingId, Long requesterId) {
        log.info("Requesting slot for booking: {} by user: {}", bookingId, requesterId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.BOOKED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("This slot is not available for request.");
        }

        // Don't allow requesting if the booking is currently active
        LocalDateTime now = LocalDateTime.now();
        if (booking.getStartTime().isBefore(now) && booking.getEndTime().isAfter(now)) {
            throw new IllegalStateException("Cannot request a slot that is currently in use.");
        }

        if (booking.getUserId().equals(requesterId)) {
            throw new IllegalStateException("You cannot request your own booking slot.");
        }

        String requesterName = appUserService.getUserById(requesterId)
                .map(user -> user.getFullName())
                .orElse("User #" + requesterId);

        Room room = roomRepository.findById(booking.getRoomId()).orElse(null);
        String roomName = room != null ? room.getRoomName() : "Room #" + booking.getRoomId();

        String timeSlot = booking.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                " to " + booking.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));

        booking.setSlotRequestUserId(requesterId);
        booking.setSlotRequestUserName(requesterName);
        booking.setSlotRequestAt(LocalDateTime.now());
        booking.setSlotRequestStatus("PENDING");
        bookingRepository.save(booking);

        // ✅ DYNAMIC URL
        String responseLink = baseUrlService.buildUrl("/bookings/slot-request/%s/respond?requesterId=%s", bookingId, requesterId);

        String currentBookerEmail = getEmailForUser(booking.getUserId());

        emailService.sendSimpleEmail(
                currentBookerEmail,
                "Slot Request for " + roomName,
                "Dear User,\n\n" +
                        requesterName + " has requested to use your booked slot for " + roomName + ".\n\n" +
                        "Time Slot: " + timeSlot + "\n\n" +
                        "Please login to respond to this request: " + responseLink + "\n\n" +
                        "Thank you,\nAssetIQ-Pro Team"
        );

        createNotification(
                booking.getUserId(),
                "SLOT_REQUEST_RECEIVED",
                "Slot Request Received",
                requesterName + " has requested to use your " + roomName + " slot (" + timeSlot + ")",
                responseLink
        );

        createNotification(
                requesterId,
                "SLOT_REQUEST_SENT",
                "Slot Request Sent",
                "You have requested to use " + roomName + " from " + timeSlot + ". Waiting for approval.",
                baseUrlService.buildUrl("/bookings/bookings-dashboard")  // ✅ DYNAMIC
        );

        auditService.logAction("SLOT_REQUESTED",
                "Slot requested for booking: " + bookingId + " by user: " + requesterId,
                requesterId);
    }

    @Transactional
    public void approveSlotRequest(Long bookingId, Long requesterId) {
        log.info("Approving slot request for booking: {} by requester: {}", bookingId, requesterId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (booking.getSlotRequestUserId() == null || !booking.getSlotRequestUserId().equals(requesterId)) {
            throw new IllegalStateException("No pending slot request found for this user.");
        }

        String requesterName = booking.getSlotRequestUserName();

        // Transfer the booking to the requester
        booking.setUserId(requesterId);
        booking.setSlotRequestUserId(null);
        booking.setSlotRequestUserName(null);
        booking.setSlotRequestAt(null);
        booking.setSlotRequestStatus("APPROVED");
        booking.setNotes("Slot transferred to " + requesterName + " on " + LocalDateTime.now());
        bookingRepository.save(booking);

        String newUserEmail = getEmailForUser(requesterId);
        emailService.sendSimpleEmail(
                newUserEmail,
                "Slot Approved - " + booking.getBookingId(),
                "Your slot request has been approved! The booking is now in your name.\n\n" +
                        "Booking Details:\n" +
                        "Room: " + roomRepository.findById(booking.getRoomId()).map(Room::getRoomName).orElse("Room") + "\n" +
                        "Time: " + booking.getStartTime() + " - " + booking.getEndTime()
        );

        auditService.logAction("SLOT_APPROVED",
                "Slot request approved for booking: " + bookingId + " by requester: " + requesterId,
                requesterId);
    }

    @Transactional
    public void declineSlotRequest(Long bookingId, Long requesterId) {
        log.info("Declining slot request for booking: {} by requester: {}", bookingId, requesterId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (booking.getSlotRequestUserId() == null || !booking.getSlotRequestUserId().equals(requesterId)) {
            throw new IllegalStateException("No pending slot request found for this user.");
        }

        booking.setSlotRequestUserId(null);
        booking.setSlotRequestUserName(null);
        booking.setSlotRequestAt(null);
        booking.setSlotRequestStatus("DECLINED");
        bookingRepository.save(booking);

        String requesterEmail = getEmailForUser(requesterId);
        emailService.sendSimpleEmail(
                requesterEmail,
                "Slot Request Declined",
                "Your slot request has been declined by the current booker."
        );

        auditService.logAction("SLOT_DECLINED",
                "Slot request declined for booking: " + bookingId + " by requester: " + requesterId,
                requesterId);
    }

    // ============================================
    // Admin Cancel Booking
    // ============================================

    @Transactional
    public void adminCancelBooking(Long bookingId, Long adminId) {
        log.info("Admin {} cancelling booking: {}", adminId, bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setNotes("Cancelled by admin: " + adminId + " at " + LocalDateTime.now());
        bookingRepository.save(booking);

        // Notify the original booker
        String userEmail = getEmailForUser(booking.getUserId());
        Room room = roomRepository.findById(booking.getRoomId()).orElse(null);
        String roomName = room != null ? room.getRoomName() : "Room #" + booking.getRoomId();

        emailService.sendSimpleEmail(
                userEmail,
                "Booking Cancelled by Admin",
                "Your booking for " + roomName + " on " +
                        booking.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                        " has been cancelled by an administrator.\n\n" +
                        "If you have any questions, please contact the administrator."
        );

        auditService.logAction("BOOKING_ADMIN_CANCELLED",
                "Booking cancelled by admin: " + bookingId + " by admin: " + adminId,
                adminId);
    }

    // ============================================
    // Get Booking by ID
    // ============================================

    public Booking getBookingById(Long bookingId) {
        log.info("Getting booking by ID: {}", bookingId);
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));
    }

    // ============================================
    // Query Methods
    // ============================================

    public boolean isRoomAvailable(Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Booking> bookings = bookingRepository.findByRoomId(roomId);
        for (Booking booking : bookings) {
            if (booking.getStatus() == BookingStatus.BOOKED ||
                    booking.getStatus() == BookingStatus.CONFIRMED ||
                    booking.getStatus() == BookingStatus.PENDING ||
                    booking.getStatus() == BookingStatus.ACTIVE) {
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
        LocalDateTime now = LocalDateTime.now();

        return bookings.stream().map(booking -> {
            BookingDTO dto = new BookingDTO();
            dto.setBookingId(booking.getBookingId());
            dto.setRoomId(booking.getRoomId());
            dto.setStartTime(booking.getStartTime());
            dto.setEndTime(booking.getEndTime());
            dto.setStatus(booking.getStatus().name());
            dto.setPurpose(booking.getPurpose());
            dto.setUserId(booking.getUserId());

            // Check if currently active
            boolean isCurrentlyActive = booking.getStartTime().isBefore(now) && booking.getEndTime().isAfter(now);
            dto.setCurrentlyActive(isCurrentlyActive);
            dto.setActive(true);

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

    public List<BookingDTO> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(this::convertToBookingDTO)
                .collect(Collectors.toList());
    }

    // ============================================
    // Booking Management
    // ============================================

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

        if (booking.getStatus() != BookingStatus.BOOKED && booking.getStatus() != BookingStatus.PENDING) {
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

    private void notifyInfrastructureTeam(Booking booking, Room room) {
        String roomName = room != null ? room.getRoomName() : "Server Room";
        // ✅ DYNAMIC URL
        String approvalLink = baseUrlService.buildUrl("/bookings/server-room/%s/respond", booking.getBookingId());

        // Try multiple role names
        List<com.stevecodes.AssetIQPro.entity.AppUser> infraUsers = new ArrayList<>();
        infraUsers.addAll(appUserService.getUsersByRole("INFRASTRUCTURE"));
        infraUsers.addAll(appUserService.getUsersByRole("INFRA"));
        infraUsers.addAll(appUserService.getUsersByRole("ADMIN"));

        // Remove duplicates
        infraUsers = infraUsers.stream()
                .distinct()
                .collect(Collectors.toList());

        if (infraUsers.isEmpty()) {
            log.error("No infrastructure approvers found in the system!");
            emailService.sendSimpleEmail(
                    "admin@company.com",
                    "Server Room Booking Requires Approval - #" + booking.getBookingId(),
                    "A server room booking requires your approval. Please login to review."
            );
            return;
        }

        log.info("Found {} infrastructure approvers to notify", infraUsers.size());

        for (com.stevecodes.AssetIQPro.entity.AppUser user : infraUsers) {
            String requesterName = appUserService.getUserById(booking.getUserId())
                    .map(u -> u.getFullName())
                    .orElse("Unknown");

            String emailBody = String.format("""
                A server room booking requires your approval:
                
                Room: %s
                Requester: %s
                Date: %s
                Time: %s - %s
                Purpose: %s
                Booking ID: #%d
                
                Please review and respond using the link below:
                %s
                
                Regards,
                AssetIQ-Pro System
                """,
                    roomName,
                    requesterName,
                    booking.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    booking.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                    booking.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                    booking.getPurpose() != null ? booking.getPurpose() : "N/A",
                    booking.getBookingId(),
                    approvalLink
            );

            emailService.sendSimpleEmail(
                    user.getEmail(),
                    "Server Room Booking Requires Approval - #" + booking.getBookingId(),
                    emailBody
            );
            log.info("Email sent to infrastructure approver: {}", user.getEmail());

            createNotification(
                    user.getUserId(),
                    "SERVER_ROOM_PENDING",
                    "Server Room Booking Pending",
                    "A server room booking requires your approval: " + roomName,
                    approvalLink
            );
        }

        log.info("Notified {} infrastructure approvers about server room booking: {}", infraUsers.size(), booking.getBookingId());
    }

    private void sendBookingConfirmation(Booking booking, Room room) {
        String roomName = room != null ? room.getRoomName() : "Room #" + booking.getRoomId();
        String userEmail = getEmailForUser(booking.getUserId());

        emailService.sendSimpleEmail(
                userEmail,
                "Room Booking Confirmed",
                "Your room booking has been confirmed!\n\n" +
                        "Room: " + roomName + "\n" +
                        "Date: " + booking.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "\n" +
                        "Time: " + booking.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")) +
                        " - " + booking.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) + "\n" +
                        "Purpose: " + (booking.getPurpose() != null ? booking.getPurpose() : "N/A") + "\n\n" +
                        "Thank you for using AssetIQ-Pro!"
        );

        createNotification(
                booking.getUserId(),
                "ROOM_BOOKING_CONFIRMED",
                "Room Booking Confirmed",
                "Your booking for " + roomName + " has been confirmed.",
                baseUrlService.buildUrl("/bookings/bookings-dashboard")  // ✅ DYNAMIC
        );
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
        dto.setNotes(booking.getNotes());

        // Add server room comment fields to DTO
        dto.setInfraComment(booking.getInfraComment());
        dto.setRequesterComment(booking.getRequesterComment());

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