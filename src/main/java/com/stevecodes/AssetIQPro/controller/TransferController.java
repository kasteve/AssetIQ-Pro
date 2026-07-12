package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.TransferDTO;
import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.service.TransferService;
import com.stevecodes.AssetIQPro.service.TransferSigningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "Asset Transfers", description = "Asset transfer management APIs")
public class TransferController {

    private final TransferService transferService;
    private final TransferSigningService signingService;

    // ============================================
    // Transfer Management
    // ============================================

    @PostMapping
    @Operation(summary = "Create a new asset transfer")
    @PreAuthorize("hasAnyAuthority('EDIT_ASSETS', 'ADMIN')")
    public ResponseEntity<Transfer> createTransfer(@Valid @RequestBody Transfer transfer) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.createTransfer(transfer));
    }

    @GetMapping
    @Operation(summary = "Get all transfers")
    public ResponseEntity<List<Transfer>> getAllTransfers() {
        return ResponseEntity.ok(transferService.getAllTransfers());
    }

    @GetMapping("/{transferId}")
    @Operation(summary = "Get transfer by ID")
    public ResponseEntity<Transfer> getTransferById(@PathVariable Integer transferId) {
        return ResponseEntity.ok(transferService.getTransferById(transferId));
    }

    @GetMapping("/asset/{assetTag}")
    @Operation(summary = "Get transfers by asset tag")
    public ResponseEntity<List<Transfer>> getTransfersByAssetTag(@PathVariable String assetTag) {
        return ResponseEntity.ok(transferService.getTransfersByAssetTag(assetTag));
    }

    // ============================================
    // Signing Workflow
    // ============================================

    @PostMapping("/{transferId}/initiate-signing")
    @Operation(summary = "Initiate signing process for a transfer")
    @PreAuthorize("hasAnyAuthority('EDIT_ASSETS', 'ADMIN')")
    public ResponseEntity<Void> initiateSigning(@PathVariable Integer transferId) {
        signingService.initiateTransferSigning(transferId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sign")
    @Operation(summary = "Sign a transfer using token")
    public ResponseEntity<Void> signTransfer(@RequestParam String token,
                                             @RequestParam String signature) {
        signingService.signTransferWithSignature(null, token, signature);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sign/validate")
    @Operation(summary = "Validate signing token")
    public ResponseEntity<Void> validateToken(@RequestParam String token) {
        signingService.validateToken(token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{transferId}/status")
    @Operation(summary = "Check if transfer is fully signed")
    public ResponseEntity<Boolean> isFullySigned(@PathVariable Integer transferId) {
        return ResponseEntity.ok(signingService.isTransferFullySigned(transferId));
    }

    @PostMapping("/{transferId}/complete")
    @Operation(summary = "Manually complete a transfer")
    @PreAuthorize("hasAnyAuthority('ADMIN')")
    public ResponseEntity<Void> completeTransfer(@PathVariable Integer transferId) {
        signingService.manuallyCheckTransferCompletion(transferId);
        return ResponseEntity.ok().build();
    }

    // ============================================
    // PDF Generation
    // ============================================

    @GetMapping("/{transferId}/pdf")
    @Operation(summary = "Get fully signed transfer PDF")
    public ResponseEntity<byte[]> getTransferPdf(@PathVariable Integer transferId) {
        byte[] pdf = signingService.getFullySignedPdf(transferId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=transfer_" + transferId + ".pdf")
                .body(pdf);
    }
}
