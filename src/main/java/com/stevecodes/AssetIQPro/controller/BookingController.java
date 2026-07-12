package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.BookingDTO;
import com.stevecodes.AssetIQPro.dto.DriverRequestDTO;
import com.stevecodes.AssetIQPro.dto.ResourceRequestDTO;
import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Room, driver, and resource booking APIs")
public class BookingController {

    private final BookingService bookingService;

    // ============================================
    // Room Bookings
    // ============================================

    @PostMapping("/rooms")
    @Operation(summary = "Create a room booking")
    @PreAuthorize("hasAnyAuthority('MANAGE_BOOKINGS', 'ADMIN')")
    public ResponseEntity<BookingDTO> createRoomBooking(@Valid @RequestBody BookingDTO bookingDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createRoomBooking(bookingDTO));
    }

    @GetMapping("/rooms/available")
    @Operation(summary = "Get available rooms")
    public ResponseEntity<List<Room>> getAvailableRooms() {
        return ResponseEntity.ok(bookingService.getAvailableRooms());
    }

    @GetMapping("/rooms/{roomId}/availability")
    @Operation(summary = "Check room availability")
    public ResponseEntity<Boolean> checkRoomAvailability(@PathVariable Long roomId,
                                                         @RequestParam LocalDateTime startTime,
                                                         @RequestParam LocalDateTime endTime) {
        return ResponseEntity.ok(bookingService.isRoomAvailable(roomId, startTime, endTime));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get bookings for a user")
    public ResponseEntity<List<BookingDTO>> getUserBookings(@PathVariable Long userId) {
        return ResponseEntity.ok(bookingService.getUserBookings(userId));
    }

    @PutMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel a booking")
    @PreAuthorize("hasAnyAuthority('MANAGE_BOOKINGS', 'ADMIN')")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long bookingId) {
        bookingService.cancelBooking(bookingId);
        return ResponseEntity.ok().build();
    }

    // ============================================
    // Driver Requests
    // ============================================

    @PostMapping("/drivers")
    @Operation(summary = "Request a driver")
    @PreAuthorize("hasAnyAuthority('MANAGE_BOOKINGS', 'ADMIN')")
    public ResponseEntity<DriverRequestDTO> requestDriver(@Valid @RequestBody DriverRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.requestDriver(request));
    }

    @GetMapping("/drivers/{driverId}/requests")
    @Operation(summary = "Get driver requests")
    public ResponseEntity<List<DriverRequestDTO>> getDriverRequests(@PathVariable Long driverId) {
        return ResponseEntity.ok(bookingService.getDriverRequests(driverId));
    }

    @PutMapping("/drivers/{requestId}/accept")
    @Operation(summary = "Accept a driver request")
    @PreAuthorize("hasAnyAuthority('APPROVE_LM', 'ADMIN')")
    public ResponseEntity<Void> acceptDriverRequest(@PathVariable Long requestId) {
        bookingService.acceptDriverRequest(requestId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/drivers/{requestId}/decline")
    @Operation(summary = "Decline a driver request")
    @PreAuthorize("hasAnyAuthority('APPROVE_LM', 'ADMIN')")
    public ResponseEntity<Void> declineDriverRequest(@PathVariable Long requestId,
                                                     @RequestParam String reason) {
        bookingService.declineDriverRequest(requestId, reason);
        return ResponseEntity.ok().build();
    }

    // ============================================
    // Resource Requests (Stationery, Apparel, etc.)
    // ============================================

    @PostMapping("/resources")
    @Operation(summary = "Request a resource")
    @PreAuthorize("hasAnyAuthority('MANAGE_BOOKINGS', 'ADMIN')")
    public ResponseEntity<ResourceRequestDTO> requestResource(@Valid @RequestBody ResourceRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.requestResource(request));
    }

    @GetMapping("/resources/{userId}")
    @Operation(summary = "Get resource requests for a user")
    public ResponseEntity<List<ResourceRequestDTO>> getUserResourceRequests(@PathVariable Long userId) {
        return ResponseEntity.ok(bookingService.getUserResourceRequests(userId));
    }

    @PutMapping("/resources/{requestId}/approve")
    @Operation(summary = "Approve a resource request")
    @PreAuthorize("hasAnyAuthority('APPROVE_LM', 'ADMIN')")
    public ResponseEntity<Void> approveResourceRequest(@PathVariable Long requestId,
                                                       @RequestParam String comment) {
        bookingService.approveResourceRequest(requestId, comment);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/resources/{requestId}/reject")
    @Operation(summary = "Reject a resource request")
    @PreAuthorize("hasAnyAuthority('APPROVE_LM', 'ADMIN')")
    public ResponseEntity<Void> rejectResourceRequest(@PathVariable Long requestId,
                                                      @RequestParam String reason) {
        bookingService.rejectResourceRequest(requestId, reason);
        return ResponseEntity.ok().build();
    }
}
