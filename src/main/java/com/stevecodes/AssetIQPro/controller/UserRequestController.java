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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
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

        return "bookings/my-requests";
    }
}