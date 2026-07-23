package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.TransferService;
import com.stevecodes.AssetIQPro.service.TransferSigningService;
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
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "Asset Transfers", description = "Asset transfer management APIs")
public class TransferController {

    private final TransferService transferService;
    private final TransferSigningService signingService;

    @PostMapping
    @Operation(summary = "Create a new asset transfer")
    @PreAuthorize("hasAnyAuthority('TRANSFER_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> createTransfer(@Valid @RequestBody Transfer transfer) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(transferService.createTransfer(transfer));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating transfer: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating transfer: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create transfer: " + e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Get all transfers")
    @PreAuthorize("hasAnyAuthority('TRANSFER_VIEW', 'VIEW_ALL_TRANSACTIONS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Transfer>> getAllTransfers() {
        return ResponseEntity.ok(transferService.getAllTransfers());
    }

    @GetMapping("/{transferId}")
    @Operation(summary = "Get transfer by ID")
    @PreAuthorize("hasAnyAuthority('TRANSFER_VIEW', 'VIEW_ALL_TRANSACTIONS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getTransferById(@PathVariable Integer transferId) {
        try {
            return ResponseEntity.ok(transferService.getTransferById(transferId));
        } catch (Exception e) {
            log.error("Error fetching transfer {}: {}", transferId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Transfer not found: " + e.getMessage()));
        }
    }

    @GetMapping("/asset/{assetTag}")
    @Operation(summary = "Get transfers by asset tag")
    @PreAuthorize("hasAnyAuthority('TRANSFER_VIEW', 'VIEW_ALL_TRANSACTIONS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Transfer>> getTransfersByAssetTag(@PathVariable String assetTag) {
        return ResponseEntity.ok(transferService.getTransfersByAssetTag(assetTag));
    }

    @PostMapping("/{transferId}/initiate-signing")
    @Operation(summary = "Initiate signing process for a transfer")
    @PreAuthorize("hasAnyAuthority('TRANSFER_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> initiateSigning(@PathVariable Integer transferId) {
        try {
            signingService.initiateTransferSigning(transferId);
            return ResponseEntity.ok(Map.of("message", "Signing initiated successfully"));
        } catch (Exception e) {
            log.error("Error initiating signing: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to initiate signing: " + e.getMessage()));
        }
    }

    @PostMapping("/sign")
    @Operation(summary = "Sign a transfer using token")
    public ResponseEntity<?> signTransfer(@RequestParam String token, @RequestParam String signature) {
        try {
            signingService.signTransferWithSignature(null, token, signature);
            return ResponseEntity.ok(Map.of("message", "Signature submitted successfully"));
        } catch (Exception e) {
            log.error("Error signing transfer: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to sign transfer: " + e.getMessage()));
        }
    }

    @GetMapping("/sign/validate")
    @Operation(summary = "Validate signing token")
    public ResponseEntity<?> validateToken(@RequestParam String token) {
        try {
            signingService.validateToken(token);
            return ResponseEntity.ok(Map.of("valid", true));
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/{transferId}/status")
    @Operation(summary = "Check if transfer is fully signed")
    public ResponseEntity<Boolean> isFullySigned(@PathVariable Integer transferId) {
        return ResponseEntity.ok(signingService.isTransferFullySigned(transferId));
    }

    @PostMapping("/{transferId}/complete")
    @Operation(summary = "Manually complete a transfer")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> completeTransfer(@PathVariable Integer transferId) {
        try {
            signingService.manuallyCheckTransferCompletion(transferId);
            return ResponseEntity.ok(Map.of("message", "Transfer completed successfully"));
        } catch (Exception e) {
            log.error("Error completing transfer: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to complete transfer: " + e.getMessage()));
        }
    }

    @GetMapping("/{transferId}/pdf")
    @Operation(summary = "Get fully signed transfer PDF")
    public ResponseEntity<?> getTransferPdf(@PathVariable Integer transferId) {
        try {
            byte[] pdf = signingService.getFullySignedPdf(transferId);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=transfer_" + transferId + ".pdf")
                    .body(pdf);
        } catch (IllegalStateException e) {
            log.warn("PDF not available: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "PDF not generated yet for this transfer."));
        } catch (Exception e) {
            log.error("Error generating PDF for transfer {}: {}", transferId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate PDF: " + e.getMessage()));
        }
    }
}