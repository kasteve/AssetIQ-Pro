package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.BookingDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Booking;
import com.stevecodes.AssetIQPro.entity.DriverRequest;
import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.repository.BookingRepository;
import com.stevecodes.AssetIQPro.repository.RoomRepository;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.BookingService;
import com.stevecodes.AssetIQPro.service.DriverService;
import com.stevecodes.AssetIQPro.service.InfraRequestService;
import com.stevecodes.AssetIQPro.service.ResourceRequestService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final BookingRepository bookingRepository;
    private final AppUserService userService;

    @GetMapping("/bookings-dashboard")
    @PreAuthorize("isAuthenticated()")
    public String myRequests(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        log.info("Loading dashboard for user: {}", userId);

        model.addAttribute("driverRequests", driverService.getRequestsByUserId(userId));

        List<AppUser> availableDrivers = driverService.getAvailableDrivers();
        model.addAttribute("availableDrivers", availableDrivers);

        List<AppUser> allDrivers = driverService.getAllDrivers();
        model.addAttribute("allDrivers", allDrivers);

        List<BookingDTO> roomBookings = bookingService.getBookingsWithUserNames(userId);
        model.addAttribute("roomBookings", roomBookings);

        List<Room> allRooms = roomRepository.findAllOrderedByName();
        model.addAttribute("allRooms", allRooms);

        LocalDateTime now = LocalDateTime.now();
        Set<Long> occupiedRoomIds = bookingRepository.findActiveBookingsAtTime(now,
                        List.of(Booking.BookingStatus.BOOKED, Booking.BookingStatus.ACTIVE, Booking.BookingStatus.CONFIRMED))
                .stream()
                .map(Booking::getRoomId)
                .collect(Collectors.toSet());

        Set<Long> availableRoomIds = allRooms.stream()
                .filter(room -> !occupiedRoomIds.contains(room.getRoomId()))
                .map(Room::getRoomId)
                .collect(Collectors.toSet());
        model.addAttribute("availableRoomIds", availableRoomIds);

        Set<Long> availableDriverIds = availableDrivers.stream().map(AppUser::getUserId).collect(Collectors.toSet());
        model.addAttribute("availableDriverIds", availableDriverIds);

        model.addAttribute("infraRequests", infraRequestService.getRequestsByRequesterId(userId));

        // ✅ NEW: Add pending approvals for drivers, admins, and super admins
        if (currentUser.isDriver() || currentUser.isAdmin()) {
            List<DriverRequest> pendingApprovals = driverService.getPendingRequestsForDriver(currentUser.getUserId());
            model.addAttribute("pendingDriverApprovals", pendingApprovals);
            log.info("Found {} pending approvals for driver: {}", pendingApprovals.size(), currentUser.getUsername());
        }

        model.addAttribute("canManageBookings", currentUser.canManageBookings());
        model.addAttribute("canViewAllRooms", currentUser.hasPermission("ROOM_VIEW_ALL"));
        model.addAttribute("canBookRoom", currentUser.hasPermission("ROOM_BOOK"));
        model.addAttribute("isDriver", currentUser.isDriver());

        return "bookings/bookings-dashboard";
    }

    @GetMapping("/room/{roomId}/bookings")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public List<BookingDTO> getRoomBookings(@PathVariable Long roomId) {
        log.info("Getting active and future bookings for room: {}", roomId);
        List<BookingDTO> allBookings = bookingService.getRoomBookingsWithUserNames(roomId);

        LocalDateTime now = LocalDateTime.now();
        return allBookings.stream()
                .filter(booking -> booking.getEndTime() == null || booking.getEndTime().isAfter(now))
                .collect(Collectors.toList());
    }

    @GetMapping("/room/{roomId}")
    @PreAuthorize("isAuthenticated()")
    public String viewRoomDetails(@PathVariable Long roomId, Model model) {
        log.info("Viewing room details for: {}", roomId);
        Room room = bookingService.getRoomWithBookings(roomId);
        List<BookingDTO> bookings = bookingService.getRoomBookingsWithUserNames(roomId);

        LocalDateTime now = LocalDateTime.now();
        List<BookingDTO> filteredBookings = bookings.stream()
                .filter(booking -> booking.getEndTime() == null || booking.getEndTime().isAfter(now))
                .collect(Collectors.toList());

        model.addAttribute("room", room);
        model.addAttribute("bookings", filteredBookings);
        return "bookings/room-details-modal";
    }

    @GetMapping("/driver/{driverId}/bookings")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public List<DriverRequest> getDriverBookings(@PathVariable Long driverId) {
        log.info("Getting bookings for driver: {}", driverId);
        return driverService.getDriverBookingsLast7Days(driverId);
    }

    @GetMapping("/driver/{driverId}/details")
    @PreAuthorize("isAuthenticated()")
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

    @PostMapping("/driver-request")
    @PreAuthorize("isAuthenticated()")
    public String createDriverRequest(@RequestParam Long userId,
                                      @RequestParam String destination,
                                      @RequestParam(required = false) Long driverId,
                                      @RequestParam(required = false) String reason,
                                      RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("=== CREATE DRIVER REQUEST ===");
            log.info("userId: {}, destination: {}, driverId: {}", userId, destination, driverId);

            String requestedBy = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"))
                    .getUsername();

            driverService.createDriverRequest(userId, destination, driverId, reason, requestedBy);
            redirectAttributes.addFlashAttribute("success", "Driver requested successfully!");
        } catch (IllegalStateException e) {
            log.error("Driver request validation error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error creating driver request: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to create driver request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/recall")
    @PreAuthorize("isAuthenticated()")
    public String recallDriverRequest(@PathVariable Long requestId,
                                      RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("Recalling driver request: {}", requestId);
            driverService.recallRequest(requestId);
            redirectAttributes.addFlashAttribute("success", "Driver request recalled successfully!");
        } catch (Exception e) {
            log.error("Error recalling driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to recall driver request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/accept")
    @PreAuthorize("hasAnyAuthority('DRIVER_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String acceptDriverRequest(@PathVariable Long requestId,
                                      @RequestParam Long driverId,
                                      RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("Driver {} accepting request: {}", driverId, requestId);
            driverService.acceptRequest(requestId, driverId);
            redirectAttributes.addFlashAttribute("success", "Driver request accepted successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to accept driver requests.");
        } catch (IllegalStateException e) {
            log.error("Error accepting driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error accepting driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to accept driver request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/decline")
    @PreAuthorize("hasAnyAuthority('DRIVER_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String declineDriverRequest(@PathVariable Long requestId,
                                       @RequestParam Long driverId,
                                       @RequestParam(required = false) String reason,
                                       RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            String declineReason = (reason != null && !reason.isEmpty()) ? reason : "No reason provided";
            log.info("Driver {} declining request: {} - Reason: {}", driverId, requestId, declineReason);
            driverService.declineRequest(requestId, driverId, declineReason);
            redirectAttributes.addFlashAttribute("success", "Driver request declined successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to decline driver requests.");
        } catch (IllegalStateException e) {
            log.error("Error declining driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error declining driver request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to decline driver request.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/complete")
    @PreAuthorize("hasAnyAuthority('DRIVER_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String completeTrip(@PathVariable Long requestId,
                               @RequestParam Long driverId,
                               RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("Driver {} completing trip for request: {}", driverId, requestId);
            driverService.completeTrip(requestId, driverId);
            redirectAttributes.addFlashAttribute("success", "Trip completed successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to complete trips.");
        } catch (IllegalStateException e) {
            log.error("Error completing trip: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error completing trip: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to complete trip.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/driver-request/{requestId}/rate")
    @PreAuthorize("isAuthenticated()")
    public String rateDriver(@PathVariable Long requestId,
                             @RequestParam int rating,
                             @RequestParam(required = false) String feedback,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            Long userId = (Long) session.getAttribute("userId");
            log.info("User {} rating driver for request: {} - Rating: {}", userId, requestId, rating);
            driverService.rateDriver(requestId, userId, rating, feedback);
            redirectAttributes.addFlashAttribute("success", "Thank you for rating your driver!");
        } catch (Exception e) {
            log.error("Error rating driver: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to rate driver.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/room")
    @PreAuthorize("hasAnyAuthority('ROOM_BOOK', 'ADMIN', 'SUPER_ADMIN')")
    public String createRoomBooking(
            @RequestParam Long userId,
            @RequestParam Long roomId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endTime,
            @RequestParam(required = false) String purpose,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("========================================");
            log.info("📝 CREATE ROOM BOOKING REQUEST");
            log.info("userId: {}", userId);
            log.info("roomId: {}", roomId);
            log.info("startTime: {}", startTime);
            log.info("endTime: {}", endTime);
            log.info("purpose: {}", purpose);
            log.info("========================================");

            if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
                log.error("❌ End time must be after start time");
                redirectAttributes.addFlashAttribute("error", "End time must be after start time.");
                return "redirect:/bookings/bookings-dashboard";
            }

            LocalDateTime now = LocalDateTime.now();
            if (startTime.isBefore(now)) {
                log.error("❌ Start time must be in the future. startTime: {}, now: {}", startTime, now);
                redirectAttributes.addFlashAttribute("error", "Start time must be in the future.");
                return "redirect:/bookings/bookings-dashboard";
            }

            LocalDateTime maxDate = now.plusDays(7);
            if (startTime.isAfter(maxDate)) {
                redirectAttributes.addFlashAttribute("error", "Bookings are only allowed within 7 days from today. Please select a date within the next 7 days.");
                return "redirect:/bookings/bookings-dashboard";
            }

            Room room = roomRepository.findById(roomId).orElse(null);
            if (room == null) {
                log.error("❌ Room not found: {}", roomId);
                redirectAttributes.addFlashAttribute("error", "Room not found.");
                return "redirect:/bookings/bookings-dashboard";
            }
            log.info("✅ Room found: {}, Type: {}", room.getRoomName(), room.getRoomType());

            if (room.getStatus() != Room.RoomStatus.AVAILABLE) {
                log.error("❌ Room is not available. Status: {}", room.getStatus());
                redirectAttributes.addFlashAttribute("error", "Room is not available for booking.");
                return "redirect:/bookings/bookings-dashboard";
            }

            BookingDTO dto = new BookingDTO();
            dto.setUserId(userId);
            dto.setRoomId(roomId);
            dto.setStartTime(startTime);
            dto.setEndTime(endTime);
            dto.setPurpose(purpose);

            log.info("📤 Calling bookingService.createRoomBooking...");
            BookingDTO result = bookingService.createRoomBooking(dto);
            log.info("✅ Booking created successfully with ID: {}", result.getBookingId());

            redirectAttributes.addFlashAttribute("success", "Room booked successfully! Check your email for confirmation.");

        } catch (IllegalStateException e) {
            log.error("❌ Booking validation error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to book rooms.");
        } catch (Exception e) {
            log.error("❌ Error booking room: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to book room: " + e.getMessage());
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/room/{bookingId}/recall")
    @PreAuthorize("isAuthenticated()")
    public String recallRoomBooking(@PathVariable Long bookingId,
                                    RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("Recalling room booking: {}", bookingId);
            bookingService.recallRoomBooking(bookingId);
            redirectAttributes.addFlashAttribute("success", "Room booking recalled successfully!");
        } catch (Exception e) {
            log.error("Error recalling room booking: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to recall room booking.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/room/{bookingId}/cancel")
    @PreAuthorize("hasAnyAuthority('ROOM_CANCEL', 'ADMIN', 'SUPER_ADMIN')")
    public String cancelRoomBooking(@PathVariable Long bookingId,
                                    RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("Cancelling room booking: {}", bookingId);
            bookingService.cancelBooking(bookingId);
            redirectAttributes.addFlashAttribute("success", "Room booking cancelled successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to cancel room bookings.");
        } catch (Exception e) {
            log.error("Error cancelling room booking: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to cancel room booking.");
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @PostMapping("/room/{bookingId}/request-slot")
    @PreAuthorize("isAuthenticated()")
    public String requestSlot(@PathVariable Long bookingId,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            Long userId = (Long) session.getAttribute("userId");
            log.info("User {} requesting slot for booking: {}", userId, bookingId);
            bookingService.requestSlot(bookingId, userId);
            redirectAttributes.addFlashAttribute("success", "Slot request sent successfully! The current booker will be notified.");
        } catch (Exception e) {
            log.error("Error requesting slot: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to request slot: " + e.getMessage());
        }
        return "redirect:/bookings/bookings-dashboard";
    }

    @GetMapping("/slot-request/{bookingId}/respond")
    @PreAuthorize("isAuthenticated()")
    public String showSlotRequestResponse(@PathVariable Long bookingId,
                                          @RequestParam Long requesterId,
                                          HttpSession session,
                                          Model model) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return "redirect:/login";
            }

            log.info("Showing slot request response page for booking: {}, requester: {}", bookingId, requesterId);

            Booking booking = bookingService.getBookingById(bookingId);
            String requesterName = userService.getUserById(requesterId)
                    .map(AppUser::getFullName)
                    .orElse("User #" + requesterId);

            Room room = roomRepository.findById(booking.getRoomId()).orElse(null);
            String roomName = room != null ? room.getRoomName() : "Room #" + booking.getRoomId();

            String bookedBy = userService.getUserById(booking.getUserId())
                    .map(AppUser::getFullName)
                    .orElse("Unknown User");

            String timeSlot = booking.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                    " - " + booking.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));

            model.addAttribute("bookingId", bookingId);
            model.addAttribute("requesterId", requesterId);
            model.addAttribute("requesterName", requesterName);
            model.addAttribute("roomName", roomName);
            model.addAttribute("bookedBy", bookedBy);
            model.addAttribute("timeSlot", timeSlot);
            model.addAttribute("purpose", booking.getPurpose());

            return "bookings/slot-request-response";
        } catch (Exception e) {
            log.error("Error showing slot request response: {}", e.getMessage());
            model.addAttribute("error", "Failed to load slot request details.");
            return "bookings/slot-request-error";
        }
    }

    @PostMapping("/slot-request/{bookingId}/approve")
    @PreAuthorize("isAuthenticated()")
    public String approveSlotRequest(@PathVariable Long bookingId,
                                     @RequestParam Long requesterId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("Approving slot request for booking: {} by requester: {}", bookingId, requesterId);
            bookingService.approveSlotRequest(bookingId, requesterId);
            redirectAttributes.addFlashAttribute("message", "Slot request approved successfully!");
            return "redirect:/bookings/slot-request/thankyou";
        } catch (Exception e) {
            log.error("Error approving slot request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to approve slot request: " + e.getMessage());
            return "redirect:/bookings/slot-request/error";
        }
    }

    @PostMapping("/slot-request/{bookingId}/decline")
    @PreAuthorize("isAuthenticated()")
    public String declineSlotRequest(@PathVariable Long bookingId,
                                     @RequestParam Long requesterId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("Declining slot request for booking: {} by requester: {}", bookingId, requesterId);
            bookingService.declineSlotRequest(bookingId, requesterId);
            redirectAttributes.addFlashAttribute("message", "Slot request declined successfully.");
            return "redirect:/bookings/slot-request/thankyou";
        } catch (Exception e) {
            log.error("Error declining slot request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to decline slot request: " + e.getMessage());
            return "redirect:/bookings/slot-request/error";
        }
    }

    @GetMapping("/slot-request/thankyou")
    public String slotRequestThankYou() {
        return "bookings/slot-request-thankyou";
    }

    @GetMapping("/slot-request/error")
    public String slotRequestError() {
        return "bookings/slot-request-error";
    }

    @GetMapping("/server-room/{bookingId}/respond")
    @PreAuthorize("hasAnyAuthority('APPROVE_INFRA', 'REVIEW_INFRA', 'ADMIN', 'SUPER_ADMIN')")
    public String showServerRoomResponse(@PathVariable Long bookingId,
                                         @RequestParam(required = false) String action,
                                         Model model) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return "redirect:/login";
            }

            log.info("Showing server room response page for booking: {}", bookingId);

            Booking booking = bookingService.getBookingById(bookingId);
            Room room = roomRepository.findById(booking.getRoomId()).orElse(null);
            String roomName = room != null ? room.getRoomName() : "Server Room";

            String requesterName = userService.getUserById(booking.getUserId())
                    .map(AppUser::getFullName)
                    .orElse("Unknown User");

            String timeSlot = booking.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                    " - " + booking.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));

            model.addAttribute("booking", booking);
            model.addAttribute("bookingId", bookingId);
            model.addAttribute("roomName", roomName);
            model.addAttribute("requesterName", requesterName);
            model.addAttribute("timeSlot", timeSlot);
            model.addAttribute("purpose", booking.getPurpose());
            model.addAttribute("action", action);

            return "bookings/server-room-approval";
        } catch (Exception e) {
            log.error("Error showing server room response: {}", e.getMessage());
            model.addAttribute("error", "Failed to load server room request details.");
            return "bookings/server-room-error";
        }
    }

    @PostMapping("/server-room/{bookingId}/approve")
    @PreAuthorize("hasAnyAuthority('APPROVE_INFRA', 'REVIEW_INFRA', 'ADMIN', 'SUPER_ADMIN')")
    public String approveServerRoom(@PathVariable Long bookingId,
                                    @RequestParam(required = false) String infraComment,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            Long userId = (Long) session.getAttribute("userId");
            log.info("User {} approving server room booking: {}", userId, bookingId);

            bookingService.approveServerRoom(bookingId, userId, infraComment);

            redirectAttributes.addFlashAttribute("message", "Server room booking approved successfully!");
            return "redirect:/bookings/server-room/thankyou";
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to approve server room bookings.");
        } catch (Exception e) {
            log.error("Error approving server room: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to approve server room: " + e.getMessage());
            return "redirect:/bookings/server-room/error";
        }
        return "redirect:/bookings/server-room/thankyou";
    }

    @PostMapping("/server-room/{bookingId}/decline")
    @PreAuthorize("hasAnyAuthority('APPROVE_INFRA', 'REVIEW_INFRA', 'ADMIN', 'SUPER_ADMIN')")
    public String declineServerRoom(@PathVariable Long bookingId,
                                    @RequestParam String declinedReason,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            Long userId = (Long) session.getAttribute("userId");
            log.info("User {} declining server room booking: {} - Reason: {}", userId, bookingId, declinedReason);

            bookingService.declineServerRoom(bookingId, userId, declinedReason);

            redirectAttributes.addFlashAttribute("message", "Server room booking declined successfully.");
            return "redirect:/bookings/server-room/thankyou";
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to decline server room bookings.");
        } catch (Exception e) {
            log.error("Error declining server room: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to decline server room: " + e.getMessage());
            return "redirect:/bookings/server-room/error";
        }
        return "redirect:/bookings/server-room/thankyou";
    }

    @PostMapping("/server-room/{bookingId}/signout-link")
    @PreAuthorize("hasAnyAuthority('APPROVE_INFRA', 'REVIEW_INFRA', 'ADMIN', 'SUPER_ADMIN')")
    public String generateServerRoomSignOutLink(@PathVariable Long bookingId,
                                                @RequestParam(required = false) String infraComment,
                                                HttpSession session,
                                                RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            Long userId = (Long) session.getAttribute("userId");
            log.info("User {} generating sign-out link for server room booking: {}", userId, bookingId);

            String link = bookingService.generateServerRoomSignOutLink(bookingId, userId, infraComment);

            redirectAttributes.addFlashAttribute("message", "Sign-out link generated and sent to requester!");
            return "redirect:/bookings/server-room/thankyou";
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to generate sign-out links.");
        } catch (Exception e) {
            log.error("Error generating sign-out link: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to generate sign-out link: " + e.getMessage());
            return "redirect:/bookings/server-room/error";
        }
        return "redirect:/bookings/server-room/thankyou";
    }

    @GetMapping("/server-room/sign-out")
    @PreAuthorize("isAuthenticated()")
    public String showServerRoomSignOut(@RequestParam String token, Model model) {
        log.info("Showing server room sign-out page for token: {}", token);
        Booking booking = bookingService.findBySignoutToken(token);

        if (booking == null) {
            model.addAttribute("error", "Invalid or expired sign-out link.");
            return "bookings/server-room-error";
        }

        if (booking.getSignoutTokenExpiry() != null &&
                booking.getSignoutTokenExpiry().isBefore(LocalDateTime.now())) {
            model.addAttribute("error", "Sign-out link has expired. Please request a new one.");
            return "bookings/server-room-error";
        }

        Room room = roomRepository.findById(booking.getRoomId()).orElse(null);
        String roomName = room != null ? room.getRoomName() : "Server Room";

        model.addAttribute("token", token);
        model.addAttribute("booking", booking);
        model.addAttribute("roomName", roomName);
        model.addAttribute("bookingId", booking.getBookingId());
        return "bookings/server-room-signout";
    }

    @PostMapping("/server-room/sign-out")
    @PreAuthorize("isAuthenticated()")
    public String completeServerRoomSignOut(@RequestParam String token,
                                            @RequestParam String signature,
                                            @RequestParam(required = false) String requesterComment,
                                            RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("Completing server room sign-out for token: {}", token);
            bookingService.completeServerRoomSignOut(token, signature, requesterComment);

            redirectAttributes.addFlashAttribute("message", "Server room sign-out completed successfully. Thank you!");
            return "redirect:/bookings/server-room/signout-thankyou";
        } catch (Exception e) {
            log.error("Error completing sign-out: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to sign out: " + e.getMessage());
            return "redirect:/bookings/server-room/signout-error";
        }
    }

    @GetMapping("/server-room/thankyou")
    public String serverRoomThankYou() {
        return "bookings/server-room-thankyou";
    }

    @GetMapping("/server-room/error")
    public String serverRoomError() {
        return "bookings/server-room-error";
    }

    @GetMapping("/server-room/signout-thankyou")
    public String signOutThankYou() {
        return "bookings/server-room-signout-thankyou";
    }

    @GetMapping("/server-room/signout-error")
    public String signOutError() {
        return "bookings/server-room-signout-error";
    }

    @PostMapping("/admin/booking/{bookingId}/cancel")
    @PreAuthorize("hasAnyAuthority('MANAGE_BOOKINGS', 'ADMIN', 'SUPER_ADMIN')")
    public String adminCancelBooking(@PathVariable Long bookingId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            Long userId = (Long) session.getAttribute("userId");
            log.info("Admin {} cancelling booking: {}", userId, bookingId);
            bookingService.adminCancelBooking(bookingId, userId);
            redirectAttributes.addFlashAttribute("success", "Booking cancelled successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to cancel bookings.");
        } catch (Exception e) {
            log.error("Error cancelling booking: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to cancel booking: " + e.getMessage());
        }
        return "redirect:/bookings/bookings-dashboard";
    }
}