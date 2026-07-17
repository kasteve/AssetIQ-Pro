package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.BookingDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
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

        // Get drivers (users with DRIVER role)
        List<AppUser> drivers = userService.getUsersByRole("DRIVER");
        model.addAttribute("availableDrivers", drivers);

        // Room bookings with user names
        List<BookingDTO> roomBookings = bookingService.getBookingsWithUserNames(userId);
        model.addAttribute("roomBookings", roomBookings);

        // Available rooms
        List<Room> availableRooms = roomRepository.findAvailableRooms(LocalDateTime.now());
        model.addAttribute("availableRooms", availableRooms);

        // Resource requests
        model.addAttribute("resourceRequests", resourceRequestService.getResourceRequestsByUserId(userId));

        // Infrastructure requests
        model.addAttribute("infraRequests", infraRequestService.getRequestsByRequesterId(userId));

        return "bookings/bookings-dashboard";
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

    @PostMapping("/driver-request/{requestId}/cancel")
    public String cancelDriverRequest(@PathVariable Long requestId,
                                      RedirectAttributes redirectAttributes) {
        try {
            driverService.recallRequest(requestId);
            redirectAttributes.addFlashAttribute("success", "Driver request cancelled successfully!");
        } catch (Exception e) {
            log.error("Error cancelling driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to cancel driver request.");
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

    @PostMapping("/room/slot-request/{requestId}/approve")
    public String approveSlotRequest(@PathVariable Long requestId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            bookingService.approveSlotRequest(requestId, userId);
            redirectAttributes.addFlashAttribute("success", "Slot request approved successfully!");
        } catch (Exception e) {
            log.error("Error approving slot request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to approve slot request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    // ============================================
    // Resource Request Endpoints
    // ============================================

    @PostMapping("/resource-request/{requestId}/recall")
    public String recallResourceRequest(@PathVariable Long requestId,
                                        RedirectAttributes redirectAttributes) {
        try {
            resourceRequestService.recallRequest(requestId);
            redirectAttributes.addFlashAttribute("success", "Resource request recalled successfully!");
        } catch (Exception e) {
            log.error("Error recalling resource request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to recall resource request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/resource-request/{requestId}/cancel")
    public String cancelResourceRequest(@PathVariable Long requestId,
                                        RedirectAttributes redirectAttributes) {
        try {
            resourceRequestService.recallRequest(requestId);
            redirectAttributes.addFlashAttribute("success", "Resource request cancelled successfully!");
        } catch (Exception e) {
            log.error("Error cancelling resource request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to cancel resource request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/resource-request/{requestId}/acknowledge")
    public String acknowledgeResourceRequest(@PathVariable Long requestId,
                                             @RequestParam String signature,
                                             HttpSession session,
                                             RedirectAttributes redirectAttributes) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            resourceRequestService.acknowledgeReceipt(requestId, userId, signature);
            redirectAttributes.addFlashAttribute("success", "Resource request acknowledged successfully!");
        } catch (Exception e) {
            log.error("Error acknowledging resource request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to acknowledge resource request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    // ============================================
    // Admin Resource Request Endpoints
    // ============================================

    @PostMapping("/resource-request/{requestId}/lm-approve")
    public String approveByLineManager(@PathVariable Long requestId,
                                       @RequestParam String comment,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
        try {
            Long lmId = (Long) session.getAttribute("userId");
            resourceRequestService.approveByLineManager(requestId, lmId, comment);
            redirectAttributes.addFlashAttribute("success", "Resource request approved successfully!");
        } catch (Exception e) {
            log.error("Error approving resource request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to approve resource request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/resource-request/{requestId}/lm-reject")
    public String rejectByLineManager(@PathVariable Long requestId,
                                      @RequestParam String reason,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes) {
        try {
            Long lmId = (Long) session.getAttribute("userId");
            resourceRequestService.rejectByLineManager(requestId, lmId, reason);
            redirectAttributes.addFlashAttribute("success", "Resource request rejected successfully!");
        } catch (Exception e) {
            log.error("Error rejecting resource request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to reject resource request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/resource-request/{requestId}/admin-accept")
    public String acceptResourceRequest(@PathVariable Long requestId,
                                        @RequestParam(required = false) String adminComment,
                                        RedirectAttributes redirectAttributes) {
        try {
            resourceRequestService.acceptResourceRequest(requestId, adminComment);
            redirectAttributes.addFlashAttribute("success", "Resource request accepted successfully!");
        } catch (Exception e) {
            log.error("Error accepting resource request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to accept resource request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/resource-request/{requestId}/admin-decline")
    public String declineResourceRequest(@PathVariable Long requestId,
                                         @RequestParam String reason,
                                         RedirectAttributes redirectAttributes) {
        try {
            resourceRequestService.declineResourceRequest(requestId, reason);
            redirectAttributes.addFlashAttribute("success", "Resource request declined successfully!");
        } catch (Exception e) {
            log.error("Error declining resource request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to decline resource request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }
}