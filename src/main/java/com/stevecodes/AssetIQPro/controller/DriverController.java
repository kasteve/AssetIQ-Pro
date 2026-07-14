package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.DriverRequest;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.repository.DriverRequestRepository;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import com.stevecodes.AssetIQPro.service.DriverService;
import com.stevecodes.AssetIQPro.service.AppUserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/bookings")
public class DriverController {

    private final DriverService driverService;
    private final AppUserService userService;
    private final DriverRequestRepository driverRequestRepository;
    private final AppUserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @GetMapping("/driver-requests")
    public String driverRequests(Model model) {
        model.addAttribute("requests", driverService.getDriverRequests());

        List<AppUser> availableDrivers = userService.getUsersWithPermission("MANAGE_DRIVER_REQUESTS");
        model.addAttribute("availableDrivers", availableDrivers);

        return "bookings/driver-requests";
    }

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
        } catch (Exception e) {
            log.error("Error creating driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create driver request.");
        }
        return "redirect:/bookings/my-requests";
    }

    @GetMapping("/driver-dashboard")
    public String driverDashboard(HttpSession session, Model model) {
        Long driverId = (Long) session.getAttribute("userId");
        if (driverId == null) {
            return "redirect:/login";
        }

        // Get pending requests for this driver (where status is PENDING)
        List<DriverRequest> pendingRequests = driverService.getPendingRequestsForDriver(driverId);

        // Get assigned requests (all requests where driverId matches)
        List<DriverRequest> assignedRequests = driverService.getDriverRequestsByDriverId(driverId);

        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("assignedRequests", assignedRequests);
        model.addAttribute("completedRequests", driverService.getCompletedRequestsForDriver(driverId));
        model.addAttribute("totalPending", pendingRequests.size());
        model.addAttribute("totalCompleted", driverService.getCompletedRequestsForDriver(driverId).size());
        model.addAttribute("driverAvailability", driverService.getDriverAvailability(driverId).orElse(null));

        return "bookings/driver-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/accept")
    public String acceptDriverRequest(@PathVariable Long requestId,
                                      @RequestParam Long driverId,
                                      RedirectAttributes redirectAttributes) {
        try {
            driverService.acceptRequest(requestId, driverId);
            redirectAttributes.addFlashAttribute("success", "Driver request accepted!");
        } catch (Exception e) {
            log.error("Error accepting driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to accept request.");
        }
        return "redirect:/bookings/driver-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/decline")
    public String declineDriverRequest(@PathVariable Long requestId,
                                       @RequestParam String reason,
                                       RedirectAttributes redirectAttributes) {
        try {
            driverService.declineRequest(requestId, reason);
            redirectAttributes.addFlashAttribute("success", "Driver request declined.");
        } catch (Exception e) {
            log.error("Error declining driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to decline request.");
        }
        return "redirect:/bookings/driver-dashboard";
    }

    @PostMapping("/driver/availability/toggle")
    @ResponseBody
    public Map<String, String> toggleAvailability(HttpSession session) {
        Long driverId = (Long) session.getAttribute("userId");
        Map<String, String> response = new HashMap<>();

        try {
            driverService.toggleDriverAvailability(driverId);
            response.put("status", "AVAILABLE");
            response.put("success", "true");
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("success", "false");
            response.put("message", e.getMessage());
        }
        return response;
    }
}