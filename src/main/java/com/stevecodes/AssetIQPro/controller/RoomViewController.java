package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        model.addAttribute("rooms", roomService.getAllRooms());
        return "admin/rooms";
    }

    @PostMapping
    public String createRoom(@RequestParam String roomName, @RequestParam(required = false) String roomType,
                             @RequestParam(defaultValue = "AVAILABLE") String status,
                             RedirectAttributes redirectAttributes) {
        try {
            Room room = new Room();
            room.setRoomName(roomName);
            room.setRoomType(roomType);
            room.setStatus(Room.RoomStatus.valueOf(status));
            roomService.createRoom(room);
            redirectAttributes.addFlashAttribute("success", "Room created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create room: " + e.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    @GetMapping("/{id}/delete")
    public String deleteRoom(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            roomService.deleteRoom(id);
            redirectAttributes.addFlashAttribute("success", "Room deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete room: " + e.getMessage());
        }
        return "redirect:/admin/rooms";
    }
}