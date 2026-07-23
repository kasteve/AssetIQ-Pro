package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/bookings")
public class BookingViewController {

    @GetMapping
    public String bookings(Model model) {
        log.info("Loading bookings page");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("canManageBookings", currentUser.canManageBookings());
        model.addAttribute("canViewAllRooms", currentUser.hasPermission("ROOM_VIEW_ALL"));
        model.addAttribute("canBookRoom", currentUser.hasPermission("ROOM_BOOK"));

        return "bookings/list";
    }
}