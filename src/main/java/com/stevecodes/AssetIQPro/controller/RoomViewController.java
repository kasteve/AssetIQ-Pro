package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/rooms")
public class RoomViewController {

    private final RoomService roomService;

    @GetMapping
    public String rooms(Model model) {
        log.info("Loading rooms page");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("rooms", roomService.getAllRooms());
        model.addAttribute("canManage", currentUser.hasAnyPermission("MANAGE_BOOKINGS", "ROOM_VIEW_ALL", "ADMIN"));

        return "admin/rooms";
    }

    @PostMapping
    public String createRoom(@RequestParam String roomName, @RequestParam(required = false) String roomType,
                             @RequestParam(defaultValue = "AVAILABLE") String status,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            if (!currentUser.hasAnyPermission("MANAGE_BOOKINGS", "ROOM_VIEW_ALL", "ADMIN")) {
                throw new AccessDeniedException("You don't have permission to create rooms.");
            }

            Room room = new Room();
            room.setRoomName(roomName);
            room.setRoomType(roomType);
            room.setStatus(Room.RoomStatus.valueOf(status));
            roomService.createRoom(room);
            redirectAttributes.addFlashAttribute("success", "Room created successfully!");
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create rooms.");
        } catch (Exception e) {
            log.error("Error creating room: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create room: " + e.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    @PostMapping("/{id}/edit")
    public String updateRoom(@PathVariable Long id, @RequestParam String roomName,
                             @RequestParam(required = false) String roomType,
                             @RequestParam(defaultValue = "AVAILABLE") String status,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            if (!currentUser.hasAnyPermission("MANAGE_BOOKINGS", "ROOM_VIEW_ALL", "ADMIN")) {
                throw new AccessDeniedException("You don't have permission to update rooms.");
            }

            roomService.updateRoom(id, roomName, roomType, status);
            redirectAttributes.addFlashAttribute("success", "Room updated successfully!");
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to update rooms.");
        } catch (Exception e) {
            log.error("Error updating room: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to update room: " + e.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    @GetMapping("/{id}/delete")
    public String deleteRoom(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            if (!currentUser.hasAnyPermission("MANAGE_BOOKINGS", "ROOM_VIEW_ALL", "ADMIN")) {
                throw new AccessDeniedException("You don't have permission to delete rooms.");
            }

            roomService.deleteRoom(id);
            redirectAttributes.addFlashAttribute("success", "Room deleted successfully!");
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to delete rooms.");
        } catch (Exception e) {
            log.error("Error deleting room: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to delete room: " + e.getMessage());
        }
        return "redirect:/admin/rooms";
    }
}