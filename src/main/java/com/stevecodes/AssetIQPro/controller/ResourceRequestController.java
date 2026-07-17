package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.ResourceRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.ResourceRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    @PreAuthorize("hasAnyAuthority('VIEW_REQUESTS', 'VIEW_ALL_TRANSACTIONS', 'ADMIN', 'APPROVE_FINANCE', 'APPROVE_INFRA')")
    public ResponseEntity<List<ResourceRequestDTO>> getAllResourceRequests() {
        return ResponseEntity.ok(resourceRequestService.getAllResourceRequests());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get resource request by ID")
    public ResponseEntity<ResourceRequestDTO> getResourceRequestById(@PathVariable Long id) {
        return ResponseEntity.ok(resourceRequestService.getResourceRequestById(id));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get resource requests by user ID")
    public ResponseEntity<List<ResourceRequestDTO>> getResourceRequestsByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(resourceRequestService.getResourceRequestsByUserId(userId));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending resource requests")
    @PreAuthorize("hasAnyAuthority('VIEW_REQUESTS', 'VIEW_ALL_TRANSACTIONS', 'ADMIN', 'APPROVE_FINANCE', 'APPROVE_INFRA')")
    public ResponseEntity<List<ResourceRequestDTO>> getPendingResourceRequests() {
        return ResponseEntity.ok(resourceRequestService.getPendingResourceRequests());
    }

    @GetMapping("/accepted")
    @Operation(summary = "Get accepted resource requests")
    @PreAuthorize("hasAnyAuthority('VIEW_REQUESTS', 'VIEW_ALL_TRANSACTIONS', 'ADMIN', 'APPROVE_FINANCE', 'APPROVE_INFRA')")
    public ResponseEntity<List<ResourceRequestDTO>> getAcceptedResourceRequests() {
        return ResponseEntity.ok(resourceRequestService.getAcceptedResourceRequests());
    }

    @GetMapping("/completed")
    @Operation(summary = "Get completed resource requests")
    @PreAuthorize("hasAnyAuthority('VIEW_REQUESTS', 'VIEW_ALL_TRANSACTIONS', 'ADMIN', 'APPROVE_FINANCE', 'APPROVE_INFRA')")
    public ResponseEntity<List<ResourceRequestDTO>> getCompletedResourceRequests() {
        return ResponseEntity.ok(resourceRequestService.getCompletedResourceRequests());
    }

    @PostMapping
    @Operation(summary = "Create a new resource request")
    @PreAuthorize("hasAnyAuthority('CREATE_REQUESTS', 'ADMIN')")
    public ResponseEntity<ResourceRequestDTO> createResourceRequest(@Valid @RequestBody ResourceRequestDTO dto) {
        Long userId = getCurrentUserId();
        dto.setUserId(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(resourceRequestService.createResourceRequest(dto));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept a resource request")
    @PreAuthorize("hasAnyAuthority('APPROVE_REQUESTS', 'APPROVE_INFRA', 'APPROVE_INFRA_REQUESTS', 'APPROVE_FINANCE', 'MANAGE_CONFIG', 'ADMIN')")
    public ResponseEntity<ResourceRequestDTO> acceptResourceRequest(
            @PathVariable Long id,
            @RequestParam(required = false) String adminComment) {
        return ResponseEntity.ok(resourceRequestService.acceptResourceRequest(id, adminComment));
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Decline a resource request")
    @PreAuthorize("hasAnyAuthority('APPROVE_REQUESTS', 'APPROVE_INFRA', 'APPROVE_INFRA_REQUESTS', 'APPROVE_FINANCE', 'MANAGE_CONFIG', 'ADMIN')")
    public ResponseEntity<ResourceRequestDTO> declineResourceRequest(
            @PathVariable Long id,
            @RequestParam String declinedReason) {
        return ResponseEntity.ok(resourceRequestService.declineResourceRequest(id, declinedReason));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a resource request")
    @PreAuthorize("hasAnyAuthority('APPROVE_REQUESTS', 'APPROVE_INFRA', 'APPROVE_INFRA_REQUESTS', 'APPROVE_FINANCE', 'MANAGE_CONFIG', 'ADMIN', 'COMPLETE_REQUESTS')")
    public ResponseEntity<ResourceRequestDTO> completeResourceRequest(
            @PathVariable Long id,
            @RequestParam(required = false) String deliveryNotes) {
        return ResponseEntity.ok(resourceRequestService.completeResourceRequest(id, deliveryNotes));
    }

    @PostMapping("/{id}/recall")
    @Operation(summary = "Recall a resource request")
    @PreAuthorize("hasAnyAuthority('CREATE_REQUESTS', 'ADMIN')")
    public ResponseEntity<Void> recallResourceRequest(@PathVariable Long id) {
        resourceRequestService.recallRequest(id);
        return ResponseEntity.ok().build();
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("No authenticated user found, using default user ID 10 for testing");
            return 10L;
        }

        String username = authentication.getName();
        log.info("Current username from SecurityContext: {}", username);

        AppUser user = userService.getUserByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        log.info("Found user ID: {}", user.getUserId());
        return user.getUserId();
    }
}