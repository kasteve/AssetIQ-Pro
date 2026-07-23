package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.InfraRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.security.Permissions;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.InfraRequestService;
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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
    @PreAuthorize("hasAnyAuthority('INFRA_REQUEST_CREATE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> createRequest(@Valid @RequestBody InfraRequestDTO dto) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            InfraRequestDTO created = requestService.createRequest(dto, currentUser.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Error creating infra request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create request: " + e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/upload-quotation")
    @Operation(summary = "Upload quotation for a request")
    @PreAuthorize("hasAnyAuthority('INFRA_REQUEST_ATTACH_QUOTATION', 'APPROVE_INFRA', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> uploadQuotation(@PathVariable Long requestId,
                                             @RequestParam("file") MultipartFile file) {
        try {
            String path = requestService.uploadQuotation(requestId, file);
            return ResponseEntity.ok(Map.of("path", path, "message", "Quotation uploaded successfully"));
        } catch (Exception e) {
            log.error("Error uploading quotation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload quotation: " + e.getMessage()));
        }
    }

    @GetMapping("/bookings-dashboard")
    @Operation(summary = "Get current user's requests")
    public ResponseEntity<?> getMyRequests() {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            return ResponseEntity.ok(requestService.getRequestsForUser(currentUser.getUserId()));
        } catch (Exception e) {
            log.error("Error getting user requests: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch requests: " + e.getMessage()));
        }
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending requests for approval")
    @PreAuthorize("hasAnyAuthority('INFRA_REQUEST_APPROVE', 'APPROVE_INFRA', 'APPROVE_INFRA_REQUESTS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<InfraRequestDTO>> getPendingRequests() {
        return ResponseEntity.ok(requestService.getRequestsByStatus(InfraRequest.RequestStatus.PENDING_INFRA_REVIEW));
    }

    @GetMapping("/finance")
    @Operation(summary = "Get finance requests")
    @PreAuthorize("hasAnyAuthority('APPROVE_FINANCE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<InfraRequestDTO>> getFinanceRequests() {
        return ResponseEntity.ok(requestService.getFinanceRequests());
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "Get request details")
    @PreAuthorize("hasAnyAuthority('INFRA_REQUEST_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getRequest(@PathVariable Long requestId) {
        try {
            return ResponseEntity.ok(requestService.getRequestById(requestId));
        } catch (Exception e) {
            log.error("Error fetching request {}: {}", requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Request not found: " + e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/approve-lm")
    @Operation(summary = "Approve by Line Manager")
    @PreAuthorize("hasAnyAuthority('APPROVE_LM', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> approveByLM(@PathVariable Long requestId, @RequestParam String comment) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            return ResponseEntity.ok(requestService.approveByLineManager(requestId, currentUser.getUserId(), comment));
        } catch (IllegalStateException e) {
            log.warn("Invalid state for approval: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error approving request by LM: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to approve: " + e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/reject-lm")
    @Operation(summary = "Reject by Line Manager")
    @PreAuthorize("hasAnyAuthority('APPROVE_LM', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> rejectByLM(@PathVariable Long requestId, @RequestParam String reason) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            if (reason == null || reason.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Reason is required"));
            }
            return ResponseEntity.ok(requestService.rejectByLineManager(requestId, currentUser.getUserId(), reason));
        } catch (Exception e) {
            log.error("Error rejecting request by LM: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reject: " + e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/review-infra")
    @Operation(summary = "Review by Infrastructure")
    @PreAuthorize("hasAnyAuthority('APPROVE_INFRA', 'REVIEW_INFRA', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> reviewByInfra(@PathVariable Long requestId,
                                           @RequestParam String comment,
                                           @RequestParam boolean approved) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            return ResponseEntity.ok(requestService.reviewByInfra(requestId, currentUser.getUserId(), comment, approved));
        } catch (Exception e) {
            log.error("Error reviewing request by Infra: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to review: " + e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/approve-finance")
    @Operation(summary = "Approve by Finance")
    @PreAuthorize("hasAnyAuthority('APPROVE_FINANCE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> approveByFinance(@PathVariable Long requestId, @RequestParam String comment) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            return ResponseEntity.ok(requestService.approveByFinance(requestId, currentUser.getUserId(), comment));
        } catch (Exception e) {
            log.error("Error approving request by Finance: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to approve: " + e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/reject-finance")
    @Operation(summary = "Reject by Finance")
    @PreAuthorize("hasAnyAuthority('APPROVE_FINANCE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> rejectByFinance(@PathVariable Long requestId, @RequestParam String reason) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            if (reason == null || reason.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Reason is required"));
            }
            return ResponseEntity.ok(requestService.rejectByFinance(requestId, currentUser.getUserId(), reason));
        } catch (Exception e) {
            log.error("Error rejecting request by Finance: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reject: " + e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/mark-procurement")
    @Operation(summary = "Mark request as in procurement")
    @PreAuthorize("hasAnyAuthority('APPROVE_FINANCE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> markProcurement(@PathVariable Long requestId,
                                             @RequestParam(required = false) String procurementOrderRef,
                                             @RequestParam(required = false) BigDecimal purchaseCost,
                                             @RequestParam(required = false) Integer supplierId) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            return ResponseEntity.ok(requestService.markProcurement(requestId, currentUser.getUserId(), procurementOrderRef, purchaseCost, supplierId));
        } catch (Exception e) {
            log.error("Error marking procurement: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to mark procurement: " + e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/mark-delivered")
    @Operation(summary = "Mark request as delivered")
    @PreAuthorize("hasAnyAuthority('APPROVE_FINANCE', 'ADMIN', 'INFRA', 'SUPER_ADMIN')")
    public ResponseEntity<?> markDelivered(@PathVariable Long requestId, @RequestParam String notes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            return ResponseEntity.ok(requestService.markDelivered(requestId, currentUser.getUserId(), notes));
        } catch (Exception e) {
            log.error("Error marking delivered: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to mark delivered: " + e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/complete")
    @Operation(summary = "Complete the request")
    @PreAuthorize("hasAnyAuthority('APPROVE_FINANCE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> completeRequest(@PathVariable Long requestId) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            return ResponseEntity.ok(requestService.completeRequest(requestId, currentUser.getUserId()));
        } catch (Exception e) {
            log.error("Error completing request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete request: " + e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/acknowledge")
    @Operation(summary = "Acknowledge receipt of delivered items")
    @PreAuthorize("hasAnyAuthority('INFRA_REQUEST_CREATE', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> acknowledgeReceipt(@PathVariable Long requestId) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            return ResponseEntity.ok(requestService.acknowledgeReceipt(requestId, currentUser.getUserId()));
        } catch (Exception e) {
            log.error("Error acknowledging receipt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to acknowledge: " + e.getMessage()));
        }
    }
}