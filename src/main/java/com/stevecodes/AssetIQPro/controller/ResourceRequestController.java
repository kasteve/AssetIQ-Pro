package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.ResourceRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.security.Permissions;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.ResourceRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/resource-requests")
@RequiredArgsConstructor
@Tag(name = "Resource Requests", description = "Resource request management APIs")
public class ResourceRequestController {

    private final ResourceRequestService resourceRequestService;
    private final AppUserService userService;

    @GetMapping
    @Operation(summary = "Get all resource requests")
    @PreAuthorize("hasAnyAuthority('RESOURCE_REQUEST_VIEW', 'VIEW_ALL_TRANSACTIONS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getAllResourceRequests() {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }

            // If admin or has full view, return all
            if (currentUser.isAdmin() || currentUser.hasPermission(Permissions.VIEW_ALL_TRANSACTIONS)) {
                return ResponseEntity.ok(resourceRequestService.getAllResourceRequests());
            }
            // Otherwise return only user's requests
            return ResponseEntity.ok(resourceRequestService.getResourceRequestsByUserId(currentUser.getUserId()));
        } catch (Exception e) {
            log.error("Error fetching resource requests: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch requests: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get resource request by ID")
    @PreAuthorize("hasAnyAuthority('RESOURCE_REQUEST_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getResourceRequestById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(resourceRequestService.getResourceRequestById(id));
        } catch (Exception e) {
            log.error("Error fetching request {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Request not found: " + e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get resource requests by user ID")
    @PreAuthorize("hasAnyAuthority('RESOURCE_REQUEST_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<ResourceRequestDTO>> getResourceRequestsByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(resourceRequestService.getResourceRequestsByUserId(userId));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending resource requests")
    @PreAuthorize("hasAnyAuthority('RESOURCE_REQUEST_VIEW', 'RESOURCE_REQUEST_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<ResourceRequestDTO>> getPendingResourceRequests() {
        return ResponseEntity.ok(resourceRequestService.getPendingResourceRequests());
    }

    @GetMapping("/accepted")
    @Operation(summary = "Get accepted resource requests")
    @PreAuthorize("hasAnyAuthority('RESOURCE_REQUEST_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<ResourceRequestDTO>> getAcceptedResourceRequests() {
        return ResponseEntity.ok(resourceRequestService.getAcceptedResourceRequests());
    }

    @GetMapping("/completed")
    @Operation(summary = "Get completed resource requests")
    @PreAuthorize("hasAnyAuthority('RESOURCE_REQUEST_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<ResourceRequestDTO>> getCompletedResourceRequests() {
        return ResponseEntity.ok(resourceRequestService.getCompletedResourceRequests());
    }

    @PostMapping
    @Operation(summary = "Create a new resource request")
    @PreAuthorize("hasAnyAuthority('RESOURCE_REQUEST_CREATE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> createResourceRequest(@Valid @RequestBody ResourceRequestDTO dto) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            dto.setUserId(currentUser.getUserId());
            dto.setRequestedBy(currentUser.getFullName());
            return ResponseEntity.status(HttpStatus.CREATED).body(resourceRequestService.createResourceRequest(dto));
        } catch (Exception e) {
            log.error("Error creating resource request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create request: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept a resource request")
    @PreAuthorize("hasAnyAuthority('RESOURCE_REQUEST_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> acceptResourceRequest(
            @PathVariable Long id,
            @RequestParam(required = false) String adminComment) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            return ResponseEntity.ok(resourceRequestService.acceptResourceRequest(id, adminComment));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error accepting request {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to accept request: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Decline a resource request")
    @PreAuthorize("hasAnyAuthority('RESOURCE_REQUEST_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> declineResourceRequest(
            @PathVariable Long id,
            @RequestParam String declinedReason) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            if (declinedReason == null || declinedReason.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Reason is required"));
            }
            return ResponseEntity.ok(resourceRequestService.declineResourceRequest(id, declinedReason));
        } catch (Exception e) {
            log.error("Error declining request {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to decline request: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a resource request")
    @PreAuthorize("hasAnyAuthority('RESOURCE_REQUEST_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> completeResourceRequest(
            @PathVariable Long id,
            @RequestParam(required = false) String deliveryNotes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            return ResponseEntity.ok(resourceRequestService.completeResourceRequest(id, deliveryNotes));
        } catch (Exception e) {
            log.error("Error completing request {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete request: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/recall")
    @Operation(summary = "Recall a resource request")
    @PreAuthorize("hasAnyAuthority('RESOURCE_REQUEST_CREATE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> recallResourceRequest(@PathVariable Long id) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            resourceRequestService.recallRequest(id);
            return ResponseEntity.ok(Map.of("message", "Request recalled successfully"));
        } catch (Exception e) {
            log.error("Error recalling request {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to recall request: " + e.getMessage()));
        }
    }
}