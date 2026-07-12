package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.InfraRequestDTO;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.service.InfraRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/infra-requests")
@RequiredArgsConstructor
@Tag(name = "Infrastructure Requests", description = "Infrastructure request management APIs")
public class InfraRequestController {

    private final InfraRequestService requestService;

    @PostMapping
    @Operation(summary = "Create a new infrastructure request")
    @PreAuthorize("hasAnyAuthority('CREATE_REQUESTS', 'ADMIN')")
    public ResponseEntity<InfraRequest> createRequest(@Valid @RequestBody InfraRequestDTO dto, Principal principal) {
        Long userId = getUserId(principal);
        InfraRequest request = requestService.createRequest(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @GetMapping("/my-requests")
    @Operation(summary = "Get current user's requests")
    public ResponseEntity<List<InfraRequest>> getMyRequests(Principal principal) {
        Long userId = getUserId(principal);
        return ResponseEntity.ok(requestService.getRequestsForUser(userId));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending requests for approval")
    @PreAuthorize("hasAnyAuthority('APPROVE_INFRA_REQUESTS', 'ADMIN')")
    public ResponseEntity<List<InfraRequest>> getPendingRequests() {
        return ResponseEntity.ok(requestService.getRequestsByStatus(InfraRequest.RequestStatus.PENDING_INFRA_REVIEW));
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "Get request details")
    public ResponseEntity<InfraRequest> getRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(requestService.getRequestById(requestId));
    }

    @PostMapping("/{requestId}/approve-lm")
    @Operation(summary = "Approve by Line Manager")
    @PreAuthorize("hasAnyAuthority('APPROVE_LM', 'ADMIN')")
    public ResponseEntity<InfraRequest> approveByLM(@PathVariable Long requestId,
                                                    @RequestParam String comment,
                                                    Principal principal) {
        Long managerId = getUserId(principal);
        return ResponseEntity.ok(requestService.approveByLineManager(requestId, managerId, comment));
    }

    @PostMapping("/{requestId}/reject-lm")
    @Operation(summary = "Reject by Line Manager")
    @PreAuthorize("hasAnyAuthority('APPROVE_LM', 'ADMIN')")
    public ResponseEntity<InfraRequest> rejectByLM(@PathVariable Long requestId,
                                                   @RequestParam String reason,
                                                   Principal principal) {
        Long managerId = getUserId(principal);
        return ResponseEntity.ok(requestService.rejectByLineManager(requestId, managerId, reason));
    }

    @PostMapping("/{requestId}/review-infra")
    @Operation(summary = "Review by Infrastructure")
    @PreAuthorize("hasAnyAuthority('APPROVE_INFRA', 'ADMIN')")
    public ResponseEntity<InfraRequest> reviewByInfra(@PathVariable Long requestId,
                                                      @RequestParam String comment,
                                                      @RequestParam boolean approved,
                                                      Principal principal) {
        Long infraId = getUserId(principal);
        return ResponseEntity.ok(requestService.reviewByInfra(requestId, infraId, comment, approved));
    }

    @PostMapping("/{requestId}/approve-finance")
    @Operation(summary = "Approve by Finance")
    @PreAuthorize("hasAnyAuthority('APPROVE_FINANCE', 'ADMIN')")
    public ResponseEntity<InfraRequest> approveByFinance(@PathVariable Long requestId,
                                                         @RequestParam String comment,
                                                         Principal principal) {
        Long financeId = getUserId(principal);
        return ResponseEntity.ok(requestService.approveByFinance(requestId, financeId, comment));
    }

    @PostMapping("/{requestId}/reject-finance")
    @Operation(summary = "Reject by Finance")
    @PreAuthorize("hasAnyAuthority('APPROVE_FINANCE', 'ADMIN')")
    public ResponseEntity<InfraRequest> rejectByFinance(@PathVariable Long requestId,
                                                        @RequestParam String reason,
                                                        Principal principal) {
        Long financeId = getUserId(principal);
        return ResponseEntity.ok(requestService.rejectByFinance(requestId, financeId, reason));
    }

    @PostMapping("/{requestId}/deliver")
    @Operation(summary = "Mark request as delivered")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'INFRA')")
    public ResponseEntity<InfraRequest> markDelivered(@PathVariable Long requestId,
                                                      @RequestParam String notes,
                                                      Principal principal) {
        Long deliveredBy = getUserId(principal);
        return ResponseEntity.ok(requestService.markDelivered(requestId, deliveredBy, notes));
    }

    @PostMapping("/{requestId}/acknowledge")
    @Operation(summary = "Acknowledge receipt of delivered items")
    @PreAuthorize("hasAnyAuthority('CREATE_REQUESTS', 'ADMIN')")
    public ResponseEntity<InfraRequest> acknowledgeReceipt(@PathVariable Long requestId,
                                                           Principal principal) {
        Long userId = getUserId(principal);
        return ResponseEntity.ok(requestService.acknowledgeReceipt(requestId, userId));
    }

    private Long getUserId(Principal principal) {
        // Extract user ID from principal
        return 1L; // Placeholder
    }
}
