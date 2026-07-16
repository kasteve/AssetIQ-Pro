package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.InfraRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.InfraRequestService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/infra-requests")
@RequiredArgsConstructor
@Tag(name = "Infrastructure Requests", description = "Infrastructure request management APIs")
public class InfraRequestController {

    private final InfraRequestService requestService;
    private final AppUserService userService;

    @PostMapping
    @Operation(summary = "Create a new infrastructure request")
    @PreAuthorize("hasAnyAuthority('CREATE_REQUESTS', 'ADMIN')")
    public ResponseEntity<InfraRequestDTO> createRequest(@Valid @RequestBody InfraRequestDTO dto) {
        Long userId = getCurrentUserId();
        log.info("Creating request for user ID: {}", userId);
        InfraRequestDTO created = requestService.createRequest(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{requestId}/upload-quotation")
    @Operation(summary = "Upload quotation for a request")
    @PreAuthorize("hasAnyAuthority('APPROVE_INFRA', 'ADMIN')")
    public ResponseEntity<String> uploadQuotation(@PathVariable Long requestId,
                                                  @RequestParam("file") MultipartFile file) {
        String path = requestService.uploadQuotation(requestId, file);
        return ResponseEntity.ok(path);
    }

    @GetMapping("/my-requests")
    @Operation(summary = "Get current user's requests")
    public ResponseEntity<List<InfraRequestDTO>> getMyRequests() {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(requestService.getRequestsForUser(userId));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending requests for approval")
    @PreAuthorize("hasAnyAuthority('APPROVE_INFRA_REQUESTS', 'ADMIN')")
    public ResponseEntity<List<InfraRequestDTO>> getPendingRequests() {
        return ResponseEntity.ok(requestService.getRequestsByStatus(InfraRequest.RequestStatus.PENDING_INFRA_REVIEW));
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "Get request details")
    public ResponseEntity<InfraRequestDTO> getRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(requestService.getRequestById(requestId));
    }

    @PostMapping("/{requestId}/approve-lm")
    @Operation(summary = "Approve by Line Manager")
    @PreAuthorize("hasAnyAuthority('APPROVE_LM', 'ADMIN')")
    public ResponseEntity<InfraRequestDTO> approveByLM(@PathVariable Long requestId,
                                                       @RequestParam String comment) {
        Long managerId = getCurrentUserId();
        return ResponseEntity.ok(requestService.approveByLineManager(requestId, managerId, comment));
    }

    @PostMapping("/{requestId}/reject-lm")
    @Operation(summary = "Reject by Line Manager")
    @PreAuthorize("hasAnyAuthority('APPROVE_LM', 'ADMIN')")
    public ResponseEntity<InfraRequestDTO> rejectByLM(@PathVariable Long requestId,
                                                      @RequestParam String reason) {
        Long managerId = getCurrentUserId();
        return ResponseEntity.ok(requestService.rejectByLineManager(requestId, managerId, reason));
    }

    @PostMapping("/{requestId}/review-infra")
    @Operation(summary = "Review by Infrastructure")
    @PreAuthorize("hasAnyAuthority('APPROVE_INFRA', 'ADMIN')")
    public ResponseEntity<InfraRequestDTO> reviewByInfra(@PathVariable Long requestId,
                                                         @RequestParam String comment,
                                                         @RequestParam boolean approved) {
        Long infraId = getCurrentUserId();
        return ResponseEntity.ok(requestService.reviewByInfra(requestId, infraId, comment, approved));
    }

    @PostMapping("/{requestId}/approve-finance")
    @Operation(summary = "Approve by Finance")
    @PreAuthorize("hasAnyAuthority('APPROVE_FINANCE', 'ADMIN')")
    public ResponseEntity<InfraRequestDTO> approveByFinance(@PathVariable Long requestId,
                                                            @RequestParam String comment) {
        Long financeId = getCurrentUserId();
        return ResponseEntity.ok(requestService.approveByFinance(requestId, financeId, comment));
    }

    @PostMapping("/{requestId}/reject-finance")
    @Operation(summary = "Reject by Finance")
    @PreAuthorize("hasAnyAuthority('APPROVE_FINANCE', 'ADMIN')")
    public ResponseEntity<InfraRequestDTO> rejectByFinance(@PathVariable Long requestId,
                                                           @RequestParam String reason) {
        Long financeId = getCurrentUserId();
        return ResponseEntity.ok(requestService.rejectByFinance(requestId, financeId, reason));
    }

    @PostMapping("/{requestId}/deliver")
    @Operation(summary = "Mark request as delivered")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'INFRA')")
    public ResponseEntity<InfraRequestDTO> markDelivered(@PathVariable Long requestId,
                                                         @RequestParam String notes) {
        Long deliveredBy = getCurrentUserId();
        return ResponseEntity.ok(requestService.markDelivered(requestId, deliveredBy, notes));
    }

    @PostMapping("/{requestId}/acknowledge")
    @Operation(summary = "Acknowledge receipt of delivered items")
    @PreAuthorize("hasAnyAuthority('CREATE_REQUESTS', 'ADMIN')")
    public ResponseEntity<InfraRequestDTO> acknowledgeReceipt(@PathVariable Long requestId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(requestService.acknowledgeReceipt(requestId, userId));
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