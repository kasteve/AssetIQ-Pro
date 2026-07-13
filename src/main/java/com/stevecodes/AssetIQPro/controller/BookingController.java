package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Booking;
import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.repository.BookingRepository;
import com.stevecodes.AssetIQPro.repository.RoomRepository;
import com.stevecodes.AssetIQPro.service.BookingService;
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
public class BookingController {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final BookingService bookingService;

    @GetMapping("/rooms")
    public String roomBookings(Model model) {
        model.addAttribute("rooms", roomRepository.findAll());
        model.addAttribute("bookings", bookingRepository.findTop5ByOrderByStartTimeDesc());
        model.addAttribute("availableRooms", roomRepository.findAvailableRooms(LocalDateTime.now()));
        return "bookings/rooms";
    }

    @PostMapping("/room")
    public String createRoomBooking(@RequestParam Long userId,
                                    @RequestParam Long roomId,
                                    @RequestParam LocalDateTime startTime,
                                    @RequestParam LocalDateTime endTime,
                                    @RequestParam(required = false) String purpose,
                                    RedirectAttributes redirectAttributes) {
        try {
            // Check if room is available
            if (!bookingService.isRoomAvailable(roomId, startTime, endTime)) {
                redirectAttributes.addFlashAttribute("error",
                        "Room is not available at the requested time. Please choose a different time.");
                return "redirect:/bookings/my-requests";
            }

            Booking booking = new Booking();
            booking.setUserId(userId);
            booking.setRoomId(roomId);
            booking.setStartTime(startTime);
            booking.setEndTime(endTime);
            booking.setPurpose(purpose);
            booking.setStatus(Booking.BookingStatus.BOOKED);

            bookingRepository.save(booking);
            redirectAttributes.addFlashAttribute("success", "Room booked successfully!");
        } catch (Exception e) {
            log.error("Error booking room: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to book room.");
        }
        return "redirect:/bookings/my-requests";
    }

    @PostMapping("/room/{bookingId}/cancel")
    public String cancelRoomBooking(@PathVariable Long bookingId,
                                    RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new RuntimeException("Booking not found"));
            booking.setStatus(Booking.BookingStatus.CANCELLED);
            bookingRepository.save(booking);
            redirectAttributes.addFlashAttribute("success", "Booking cancelled.");
        } catch (Exception e) {
            log.error("Error cancelling booking: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to cancel booking.");
        }
        return "redirect:/bookings/my-requests";
    }

    @GetMapping("/available-rooms")
    @ResponseBody
    public List<Room> getAvailableRooms() {
        return roomRepository.findAvailableRooms(LocalDateTime.now());
    }
}