package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.BookingDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Booking;
import com.stevecodes.AssetIQPro.entity.DriverRequest;
import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.repository.BookingRepository;
import com.stevecodes.AssetIQPro.repository.RoomRepository;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.BookingService;
import com.stevecodes.AssetIQPro.service.DriverService;
import com.stevecodes.AssetIQPro.service.InfraRequestService;
import com.stevecodes.AssetIQPro.service.ResourceRequestService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/bookings")
public class UserRequestController {

    private final DriverService driverService;
    private final BookingService bookingService;
    private final ResourceRequestService resourceRequestService;
    private final InfraRequestService infraRequestService;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final AppUserService userService;

    @GetMapping("/bookings-dashboard")
    public String myRequests(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        log.info("Loading dashboard for user: {}", userId);

        // Driver requests
        model.addAttribute("driverRequests", driverService.getRequestsByUserId(userId));

        // Get available drivers with booking counts
        List<AppUser> availableDrivers = driverService.getAvailableDrivers();
        model.addAttribute("availableDrivers", availableDrivers);

        // All drivers (for the modal popup)
        List<AppUser> allDrivers = driverService.getAllDrivers();
        model.addAttribute("allDrivers", allDrivers);

        // Room bookings with user names
        List<BookingDTO> roomBookings = bookingService.getBookingsWithUserNames(userId);
        model.addAttribute("roomBookings", roomBookings);

        // All rooms (for the Book Room modal)
        List<Room> allRooms = roomRepository.findAllOrderedByName();
        model.addAttribute("allRooms", allRooms);

        // Calculate actual availability based on current bookings
        LocalDateTime now = LocalDateTime.now();
        Set<Long> occupiedRoomIds = bookingRepository.findActiveBookingsAtTime(now,
                        List.of(Booking.BookingStatus.BOOKED, Booking.BookingStatus.ACTIVE, Booking.BookingStatus.CONFIRMED))
                .stream()
                .map(Booking::getRoomId)
                .collect(Collectors.toSet());

        // IDs of currently-available rooms (not occupied)
        Set<Long> availableRoomIds = allRooms.stream()
                .filter(room -> !occupiedRoomIds.contains(room.getRoomId()))
                .map(Room::getRoomId)
                .collect(Collectors.toSet());
        model.addAttribute("availableRoomIds", availableRoomIds);

        // IDs of currently-available drivers
        List<Room> availableRooms = roomRepository.findAvailableRooms(LocalDateTime.now());
        Set<Long> availableDriverIds = availableDrivers.stream().map(AppUser::getUserId).collect(Collectors.toSet());
        model.addAttribute("availableDriverIds", availableDriverIds);

        // Infrastructure requests
        model.addAttribute("infraRequests", infraRequestService.getRequestsByRequesterId(userId));

        return "bookings/bookings-dashboard";
    }

    // ============================================
    // Room Details - Get active and future bookings for a room
    // ============================================

    @GetMapping("/room/{roomId}/bookings")
    @ResponseBody
    public List<BookingDTO> getRoomBookings(@PathVariable Long roomId) {
        log.info("Getting active and future bookings for room: {}", roomId);
        List<BookingDTO> allBookings = bookingService.getRoomBookingsWithUserNames(roomId);

        // Filter to only show active and future bookings (not past)
        LocalDateTime now = LocalDateTime.now();
        return allBookings.stream()
                .filter(booking -> booking.getEndTime() == null || booking.getEndTime().isAfter(now))
                .collect(Collectors.toList());
    }

    @GetMapping("/room/{roomId}")
    public String viewRoomDetails(@PathVariable Long roomId, Model model) {
        log.info("Viewing room details for: {}", roomId);
        Room room = bookingService.getRoomWithBookings(roomId);
        List<BookingDTO> bookings = bookingService.getRoomBookingsWithUserNames(roomId);

        // Filter to only show active and future bookings
        LocalDateTime now = LocalDateTime.now();
        List<BookingDTO> filteredBookings = bookings.stream()
                .filter(booking -> booking.getEndTime() == null || booking.getEndTime().isAfter(now))
                .collect(Collectors.toList());

        model.addAttribute("room", room);
        model.addAttribute("bookings", filteredBookings);
        return "bookings/room-details-modal";
    }

    // ============================================
    // Driver Details - Get driver bookings
    // ============================================

    @GetMapping("/driver/{driverId}/bookings")
    @ResponseBody
    public List<DriverRequest> getDriverBookings(@PathVariable Long driverId) {
        log.info("Getting bookings for driver: {}", driverId);
        return driverService.getDriverBookingsLast7Days(driverId);
    }

    @GetMapping("/driver/{driverId}/details")
    public String viewDriverDetails(@PathVariable Long driverId, Model model) {
        log.info("Viewing driver details for: {}", driverId);
        AppUser driver = userService.getUserById(driverId).orElse(null);
        int bookingCount = driverService.getDriverBookingCountLast7Days(driverId);
        List<DriverRequest> bookings = driverService.getDriverBookingsLast7Days(driverId);

        model.addAttribute("driver", driver);
        model.addAttribute("bookingCount", bookingCount);
        model.addAttribute("bookings", bookings);
        return "bookings/driver-details-modal";
    }

    // ============================================
    // Driver Request Endpoints
    // ============================================

    @PostMapping("/driver-request")
    public String createDriverRequest(@RequestParam Long userId,
                                      @RequestParam String destination,
                                      @RequestParam(required = false) Long driverId,
                                      @RequestParam(required = false) String reason,
                                      RedirectAttributes redirectAttributes) {
        try {
            log.info("=== CREATE DRIVER REQUEST ===");
            log.info("userId: {}, destination: {}, driverId: {}", userId, destination, driverId);

            String requestedBy = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"))
                    .getUsername();

            driverService.createDriverRequest(userId, destination, driverId, reason, requestedBy);
            redirectAttributes.addFlashAttribute("success", "Driver requested successfully!");
        } catch (IllegalStateException e) {
            log.error("Driver request validation error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error creating driver request: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to create driver request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/recall")
    public String recallDriverRequest(@PathVariable Long requestId,
                                      RedirectAttributes redirectAttributes) {
        try {
            log.info("Recalling driver request: {}", requestId);
            driverService.recallRequest(requestId);
            redirectAttributes.addFlashAttribute("success", "Driver request recalled successfully!");
        } catch (Exception e) {
            log.error("Error recalling driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to recall driver request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/accept")
    public String acceptDriverRequest(@PathVariable Long requestId,
                                      @RequestParam Long driverId,
                                      RedirectAttributes redirectAttributes) {
        try {
            log.info("Driver {} accepting request: {}", driverId, requestId);
            driverService.acceptRequest(requestId, driverId);
            redirectAttributes.addFlashAttribute("success", "Driver request accepted successfully!");
        } catch (Exception e) {
            log.error("Error accepting driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to accept driver request.");
        }
        return "redirect:/bookings/driver-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/decline")
    public String declineDriverRequest(@PathVariable Long requestId,
                                       @RequestParam Long driverId,
                                       @RequestParam String reason,
                                       RedirectAttributes redirectAttributes) {
        try {
            log.info("Driver {} declining request: {} - Reason: {}", driverId, requestId, reason);
            driverService.declineRequest(requestId, driverId, reason);
            redirectAttributes.addFlashAttribute("success", "Driver request declined successfully!");
        } catch (Exception e) {
            log.error("Error declining driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to decline driver request.");
        }
        return "redirect:/bookings/driver-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/complete")
    public String completeTrip(@PathVariable Long requestId,
                               @RequestParam Long driverId,
                               RedirectAttributes redirectAttributes) {
        try {
            log.info("Driver {} completing trip for request: {}", driverId, requestId);
            driverService.completeTrip(requestId, driverId);
            redirectAttributes.addFlashAttribute("success", "Trip completed successfully!");
        } catch (Exception e) {
            log.error("Error completing trip: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to complete trip.");
        }
        return "redirect:/bookings/driver-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/rate")
    public String rateDriver(@PathVariable Long requestId,
                             @RequestParam int rating,
                             @RequestParam(required = false) String feedback,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            log.info("User {} rating driver for request: {} - Rating: {}", userId, requestId, rating);
            driverService.rateDriver(requestId, userId, rating, feedback);
            redirectAttributes.addFlashAttribute("success", "Thank you for rating your driver!");
        } catch (Exception e) {
            log.error("Error rating driver: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to rate driver.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    // ============================================
    // Room Booking Endpoint
    // ============================================

    @PostMapping("/room")
    public String createRoomBooking(
            @RequestParam Long userId,
            @RequestParam Long roomId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endTime,
            @RequestParam(required = false) String purpose,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            log.info("========================================");
            log.info("📝 CREATE ROOM BOOKING REQUEST");
            log.info("userId: {}", userId);
            log.info("roomId: {}", roomId);
            log.info("startTime: {}", startTime);
            log.info("endTime: {}", endTime);
            log.info("purpose: {}", purpose);
            log.info("========================================");

            // Validate that end time is after start time
            if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
                log.error("❌ End time must be after start time");
                redirectAttributes.addFlashAttribute("error", "End time must be after start time.");
                return "redirect:/bookings/bookings-dashboard";
            }

            // Validate that start time is in the future
            LocalDateTime now = LocalDateTime.now();
            if (startTime.isBefore(now)) {
                log.error("❌ Start time must be in the future. startTime: {}, now: {}", startTime, now);
                redirectAttributes.addFlashAttribute("error", "Start time must be in the future.");
                return "redirect:/bookings/bookings-dashboard";
            }

            // Validate that booking is within 7 days
            LocalDateTime maxDate = now.plusDays(7);
            if (startTime.isAfter(maxDate)) {
                redirectAttributes.addFlashAttribute("error", "Bookings are only allowed within 7 days from today. Please select a date within the next 7 days.");
                return "redirect:/bookings/bookings-dashboard";
            }

            // Check if room exists
            Room room = roomRepository.findById(roomId).orElse(null);
            if (room == null) {
                log.error("❌ Room not found: {}", roomId);
                redirectAttributes.addFlashAttribute("error", "Room not found.");
                return "redirect:/bookings/bookings-dashboard";
            }
            log.info("✅ Room found: {}, Type: {}", room.getRoomName(), room.getRoomType());

            // Check if room is available
            if (room.getStatus() != Room.RoomStatus.AVAILABLE) {
                log.error("❌ Room is not available. Status: {}", room.getStatus());
                redirectAttributes.addFlashAttribute("error", "Room is not available for booking.");
                return "redirect:/bookings/bookings-dashboard";
            }

            BookingDTO dto = new BookingDTO();
            dto.setUserId(userId);
            dto.setRoomId(roomId);
            dto.setStartTime(startTime);
            dto.setEndTime(endTime);
            dto.setPurpose(purpose);

            log.info("📤 Calling bookingService.createRoomBooking...");
            BookingDTO result = bookingService.createRoomBooking(dto);
            log.info("✅ Booking created successfully with ID: {}", result.getBookingId());

            redirectAttributes.addFlashAttribute("success", "Room booked successfully! Check your email for confirmation.");

        } catch (IllegalStateException e) {
            log.error("❌ Booking validation error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error booking room: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to book room: " + e.getMessage());
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/room/{bookingId}/recall")
    public String recallRoomBooking(@PathVariable Long bookingId,
                                    RedirectAttributes redirectAttributes) {
        try {
            log.info("Recalling room booking: {}", bookingId);
            bookingService.recallRoomBooking(bookingId);
            redirectAttributes.addFlashAttribute("success", "Room booking recalled successfully!");
        } catch (Exception e) {
            log.error("Error recalling room booking: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to recall room booking.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/room/{bookingId}/cancel")
    public String cancelRoomBooking(@PathVariable Long bookingId,
                                    RedirectAttributes redirectAttributes) {
        try {
            log.info("Cancelling room booking: {}", bookingId);
            bookingService.cancelBooking(bookingId);
            redirectAttributes.addFlashAttribute("success", "Room booking cancelled successfully!");
        } catch (Exception e) {
            log.error("Error cancelling room booking: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to cancel room booking.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/room/{bookingId}/request-slot")
    public String requestSlot(@PathVariable Long bookingId,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            log.info("User {} requesting slot for booking: {}", userId, bookingId);
            bookingService.requestSlot(bookingId, userId);
            redirectAttributes.addFlashAttribute("success", "Slot request sent successfully! The current booker will be notified.");
        } catch (Exception e) {
            log.error("Error requesting slot: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to request slot: " + e.getMessage());
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @GetMapping("/slot-request/{bookingId}/respond")
    public String showSlotRequestResponse(@PathVariable Long bookingId,
                                          @RequestParam Long requesterId,
                                          HttpSession session,
                                          Model model) {
        try {
            log.info("Showing slot request response page for booking: {}, requester: {}", bookingId, requesterId);

            Booking booking = bookingService.getBookingById(bookingId);
            String requesterName = userService.getUserById(requesterId)
                    .map(AppUser::getFullName)
                    .orElse("User #" + requesterId);

            // Get room details
            Room room = roomRepository.findById(booking.getRoomId()).orElse(null);
            String roomName = room != null ? room.getRoomName() : "Room #" + booking.getRoomId();

            // Get booked by name
            String bookedBy = userService.getUserById(booking.getUserId())
                    .map(AppUser::getFullName)
                    .orElse("Unknown User");

            // Format time slot
            String timeSlot = booking.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                    " - " + booking.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));

            model.addAttribute("bookingId", bookingId);
            model.addAttribute("requesterId", requesterId);
            model.addAttribute("requesterName", requesterName);
            model.addAttribute("roomName", roomName);
            model.addAttribute("bookedBy", bookedBy);
            model.addAttribute("timeSlot", timeSlot);
            model.addAttribute("purpose", booking.getPurpose());

            return "bookings/slot-request-response";
        } catch (Exception e) {
            log.error("Error showing slot request response: {}", e.getMessage());
            model.addAttribute("error", "Failed to load slot request details.");
            return "bookings/slot-request-error";
        }
    }

    // Add these methods to UserRequestController.java

    @PostMapping("/slot-request/{bookingId}/approve")
    public String approveSlotRequest(@PathVariable Long bookingId,
                                     @RequestParam Long requesterId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        try {
            log.info("Approving slot request for booking: {} by requester: {}", bookingId, requesterId);
            bookingService.approveSlotRequest(bookingId, requesterId);
            redirectAttributes.addFlashAttribute("message", "Slot request approved successfully!");
            return "redirect:/bookings/slot-request/thankyou";
        } catch (Exception e) {
            log.error("Error approving slot request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to approve slot request: " + e.getMessage());
            return "redirect:/bookings/slot-request/error";
        }
    }

    @PostMapping("/slot-request/{bookingId}/decline")
    public String declineSlotRequest(@PathVariable Long bookingId,
                                     @RequestParam Long requesterId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        try {
            log.info("Declining slot request for booking: {} by requester: {}", bookingId, requesterId);
            bookingService.declineSlotRequest(bookingId, requesterId);
            redirectAttributes.addFlashAttribute("message", "Slot request declined successfully.");
            return "redirect:/bookings/slot-request/thankyou";
        } catch (Exception e) {
            log.error("Error declining slot request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to decline slot request: " + e.getMessage());
            return "redirect:/bookings/slot-request/error";
        }
    }

    @GetMapping("/slot-request/thankyou")
    public String slotRequestThankYou(Model model, RedirectAttributes redirectAttributes) {
        log.info("Showing slot request thank you page");
        return "bookings/slot-request-thankyou";
    }

    @GetMapping("/slot-request/error")
    public String slotRequestError(Model model, RedirectAttributes redirectAttributes) {
        log.info("Showing slot request error page");
        return "bookings/slot-request-error";
    }

    @PostMapping("/admin/booking/{bookingId}/cancel")
    public String adminCancelBooking(@PathVariable Long bookingId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            log.info("Admin {} cancelling booking: {}", userId, bookingId);
            bookingService.adminCancelBooking(bookingId, userId);
            redirectAttributes.addFlashAttribute("success", "Booking cancelled successfully!");
        } catch (Exception e) {
            log.error("Error cancelling booking: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to cancel booking: " + e.getMessage());
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    // ============================================
    // Server Room - Infrastructure Approval
    // ============================================

    @PostMapping("/server-room/{bookingId}/approve")
    public String approveServerRoom(@PathVariable Long bookingId,
                                    @RequestParam String comment,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            log.info("User {} approving server room booking: {}", userId, bookingId);
            bookingService.approveServerRoom(bookingId, userId, comment);
            redirectAttributes.addFlashAttribute("success", "Server room booking approved successfully!");
        } catch (Exception e) {
            log.error("Error approving server room: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to approve server room.");
        }
        return "redirect:/admin/server-room-requests";
    }

    @PostMapping("/server-room/{bookingId}/decline")
    public String declineServerRoom(@PathVariable Long bookingId,
                                    @RequestParam String reason,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            log.info("User {} declining server room booking: {} - Reason: {}", userId, bookingId, reason);
            bookingService.declineServerRoom(bookingId, userId, reason);
            redirectAttributes.addFlashAttribute("success", "Server room booking declined successfully!");
        } catch (Exception e) {
            log.error("Error declining server room: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to decline server room.");
        }
        return "redirect:/admin/server-room-requests";
    }

    @PostMapping("/server-room/{bookingId}/signout")
    public String generateServerRoomSignOut(@PathVariable Long bookingId,
                                            HttpSession session,
                                            RedirectAttributes redirectAttributes) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            log.info("User {} generating sign-out link for server room booking: {}", userId, bookingId);
            String link = bookingService.generateServerRoomSignOutLink(bookingId, userId);
            redirectAttributes.addFlashAttribute("success", "Sign-out link generated and sent to requester: " + link);
        } catch (Exception e) {
            log.error("Error generating sign-out link: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to generate sign-out link.");
        }
        return "redirect:/admin/server-room-requests";
    }

    @GetMapping("/server-room/sign-out")
    public String showServerRoomSignOut(@RequestParam String token, Model model) {
        log.info("Showing server room sign-out page for token: {}", token);
        model.addAttribute("token", token);
        return "bookings/server-room-signout";
    }

    @PostMapping("/server-room/sign-out")
    public String completeServerRoomSignOut(@RequestParam String token,
                                            @RequestParam String signature,
                                            RedirectAttributes redirectAttributes) {
        try {
            log.info("Completing server room sign-out for token: {}", token);
            bookingService.completeServerRoomSignOut(token, signature);
            redirectAttributes.addFlashAttribute("message", "Server room sign-out completed successfully. Thank you!");
            return "redirect:/bookings/server-room/signout-thankyou";
        } catch (Exception e) {
            log.error("Error completing sign-out: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to sign out: " + e.getMessage());
            return "redirect:/bookings/server-room/signout-error";
        }
    }

    @GetMapping("/server-room/signout-thankyou")
    public String signOutThankYou() {
        return "bookings/server-room-signout-thankyou";
    }

    @GetMapping("/server-room/signout-error")
    public String signOutError() {
        return "bookings/server-room-signout-error";
    }
}