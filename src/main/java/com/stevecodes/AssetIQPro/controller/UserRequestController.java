package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.repository.RoomRepository;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.BookingService;
import com.stevecodes.AssetIQPro.service.DriverService;
import com.stevecodes.AssetIQPro.service.InfraRequestService;
import com.stevecodes.AssetIQPro.service.ResourceRequestService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

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

    @GetMapping("/my-requests")
    public String myRequests(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        model.addAttribute("driverRequests", driverService.getRequestsByUserId(userId));
        model.addAttribute("roomBookings", bookingService.getBookingsByUserId(userId));
        model.addAttribute("resourceRequests", resourceRequestService.getResourceRequestsByUserId(userId));
        model.addAttribute("infraRequests", infraRequestService.getRequestsByRequesterId(userId));

        // Add available rooms for dropdown
        List<Room> availableRooms = roomRepository.findAvailableRooms(java.time.LocalDateTime.now());
        model.addAttribute("availableRooms", availableRooms);

        // Add available drivers (users with DRIVER role or MANAGE_DRIVER_REQUESTS permission)
        List<AppUser> availableDrivers = userService.getUsersWithPermission("MANAGE_DRIVER_REQUESTS");
        model.addAttribute("availableDrivers", availableDrivers);

        return "bookings/my-requests";
    }
}