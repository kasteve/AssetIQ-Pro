package com.stevecodes.AssetIQPro.controller;

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

@Controller
@RequiredArgsConstructor
@RequestMapping("/bookings")
public class UserRequestController {

    private final DriverService driverService;
    private final BookingService bookingService;
    private final ResourceRequestService resourceRequestService;
    private final InfraRequestService infraRequestService;

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

        return "bookings/my-requests";
    }
}