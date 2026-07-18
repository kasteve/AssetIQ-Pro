package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.BookingDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.DriverRequest;
import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.repository.RoomRepository;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.BookingService;
import com.stevecodes.AssetIQPro.service.DriverService;
import com.stevecodes.AssetIQPro.service.InfraRequestService;
import com.stevecodes.AssetIQPro.service.ResourceRequestService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

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
    private final AppUserService userService;

    @GetMapping("/bookings-dashboard")
    public String myRequests(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

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
        model.addAttribute("allRooms", roomRepository.findAll());

        // Available rooms
        List<Room> availableRooms = roomRepository.findAvailableRooms(LocalDateTime.now());
        model.addAttribute("availableRooms", availableRooms);

        // Infrastructure requests
        model.addAttribute("infraRequests", infraRequestService.getRequestsByRequesterId(userId));

        return "bookings/bookings-dashboard";
    }

    // ============================================
    // Room Details - Get all bookings for a room
    // ============================================

    @GetMapping("/room/{roomId}/bookings")
    @ResponseBody
    public List<BookingDTO> getRoomBookings(@PathVariable Long roomId) {
        log.info("Getting all bookings for room: {}", roomId);
        return bookingService.getRoomBookingsWithUserNames(roomId);
    }

    @GetMapping("/room/{roomId}")
    public String viewRoomDetails(@PathVariable Long roomId, Model model) {
        log.info("Viewing room details for: {}", roomId);
        Room room = bookingService.getRoomWithBookings(roomId);
        List<BookingDTO> bookings = bookingService.getRoomBookingsWithUserNames(roomId);

        model.addAttribute("room", room);
        model.addAttribute("bookings", bookings);
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
            String requestedBy = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"))
                    .getUsername();

            driverService.createDriverRequest(userId, destination, driverId, reason, requestedBy);
            redirectAttributes.addFlashAttribute("success", "Driver requested successfully!");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error creating driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create driver request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/recall")
    public String recallDriverRequest(@PathVariable Long requestId,
                                      RedirectAttributes redirectAttributes) {
        try {
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
            driverService.rateDriver(requestId, userId, rating, feedback);
            redirectAttributes.addFlashAttribute("success", "Thank you for rating your driver!");
        } catch (Exception e) {
            log.error("Error rating driver: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to rate driver.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    // ============================================
    // Room Booking Endpoints
    // ============================================

    @PostMapping("/room")
    public String createRoomBooking(@RequestParam Long userId,
                                    @RequestParam Long roomId,
                                    @RequestParam LocalDateTime startTime,
                                    @RequestParam LocalDateTime endTime,
                                    @RequestParam(required = false) String purpose,
                                    RedirectAttributes redirectAttributes) {
        try {
            BookingDTO dto = new BookingDTO();
            dto.setUserId(userId);
            dto.setRoomId(roomId);
            dto.setStartTime(startTime);
            dto.setEndTime(endTime);
            dto.setPurpose(purpose);

            bookingService.createRoomBooking(dto);
            redirectAttributes.addFlashAttribute("success", "Room booked successfully!");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error booking room: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to book room.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/room/{bookingId}/recall")
    public String recallRoomBooking(@PathVariable Long bookingId,
                                    RedirectAttributes redirectAttributes) {
        try {
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
            bookingService.requestSlot(bookingId, userId);
            redirectAttributes.addFlashAttribute("success", "Slot request sent successfully! The current booker will be notified.");
        } catch (Exception e) {
            log.error("Error requesting slot: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to request slot: " + e.getMessage());
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/slot-request/{bookingId}/approve")
    public String approveSlotRequest(@PathVariable Long bookingId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            bookingService.approveSlotRequest(bookingId, userId);
            redirectAttributes.addFlashAttribute("success", "Slot request approved successfully!");
        } catch (Exception e) {
            log.error("Error approving slot request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to approve slot request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/slot-request/{bookingId}/decline")
    public String declineSlotRequest(@PathVariable Long bookingId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            bookingService.declineSlotRequest(bookingId, userId);
            redirectAttributes.addFlashAttribute("success", "Slot request declined successfully!");
        } catch (Exception e) {
            log.error("Error declining slot request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to decline slot request.");
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
        model.addAttribute("token", token);
        return "bookings/server-room-signout";
    }

    @PostMapping("/server-room/sign-out")
    public String completeServerRoomSignOut(@RequestParam String token,
                                            @RequestParam String signature,
                                            RedirectAttributes redirectAttributes) {
        try {
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