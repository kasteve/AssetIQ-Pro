package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.TransferDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.entity.TransferToken;
import com.stevecodes.AssetIQPro.repository.TransferRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "Asset Transfers", description = "Asset transfer management APIs")
public class TransferController {

    private final TransferService transferService;
    private final TransferSigningService signingService;
    private final TransferRepository transferRepository;

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
            Transfer created = transferService.createTransfer(transfer);
            return ResponseEntity.status(HttpStatus.CREATED).body(TransferDTO.fromEntity(created));
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
    public ResponseEntity<List<TransferDTO>> getAllTransfers() {
        List<Transfer> transfers = transferService.getAllTransfers();
        List<TransferDTO> dtos = transfers.stream()
                .map(TransferDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{transferId}")
    @Operation(summary = "Get transfer by ID")
    @PreAuthorize("hasAnyAuthority('TRANSFER_VIEW', 'VIEW_ALL_TRANSACTIONS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getTransferById(@PathVariable Long transferId) {
        try {
            Transfer transfer = transferService.getTransferById(transferId);
            TransferDTO dto = TransferDTO.fromEntity(transfer);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Error fetching transfer {}: {}", transferId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Transfer not found: " + e.getMessage()));
        }
    }

    @GetMapping("/asset/{assetTag}")
    @Operation(summary = "Get transfers by asset tag")
    @PreAuthorize("hasAnyAuthority('TRANSFER_VIEW', 'VIEW_ALL_TRANSACTIONS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<TransferDTO>> getTransfersByAssetTag(@PathVariable String assetTag) {
        List<Transfer> transfers = transferService.getTransfersByAssetTag(assetTag);
        List<TransferDTO> dtos = transfers.stream()
                .map(TransferDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{transferId}/initiate-signing")
    @Operation(summary = "Initiate signing process for a transfer")
    @PreAuthorize("hasAnyAuthority('TRANSFER_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> initiateSigning(@PathVariable Long transferId) {
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
            TransferToken tokenObj = signingService.validateToken(token);
            signingService.signTransferWithSignature(tokenObj.getTransferId(), token, signature);
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
    public ResponseEntity<Boolean> isFullySigned(@PathVariable Long transferId) {
        return ResponseEntity.ok(signingService.isTransferFullySigned(transferId));
    }

    @PostMapping("/{transferId}/complete")
    @Operation(summary = "Manually complete a transfer")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> completeTransfer(@PathVariable Long transferId) {
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
    public ResponseEntity<?> getTransferPdf(@PathVariable Long transferId) {
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

    @PostMapping("/retransfer")
    @Operation(summary = "Re-transfer an asset to a new employee")
    @PreAuthorize("hasAnyAuthority('TRANSFER_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> retransferAsset(@RequestParam Long sourceTransferId,
                                             @RequestParam Long newEmployeeId,
                                             @RequestParam(required = false) Integer newDepartmentId,
                                             @RequestParam(required = false) String conditionOld,
                                             @RequestParam(required = false) String conditionNew,
                                             @RequestParam(required = false) String accessoriesOld,
                                             @RequestParam(required = false) String accessoriesNew,
                                             @RequestParam(required = false) String softwareInstalled,
                                             @RequestParam(required = false) String comments) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }

            Transfer sourceTransfer = transferService.getTransferById(sourceTransferId);

            Transfer retransferData = new Transfer();
            retransferData.setAssetTag(sourceTransfer.getAssetTag());
            retransferData.setPreviousTransferId(sourceTransferId);
            retransferData.setNewEmployeeId(newEmployeeId);
            retransferData.setNewDepartmentId(newDepartmentId);
            retransferData.setConditionOld(conditionOld);
            retransferData.setConditionNew(conditionNew);
            retransferData.setAccessoriesOld(accessoriesOld);
            retransferData.setAccessoriesNew(accessoriesNew);
            retransferData.setSoftwareInstalled(softwareInstalled);
            retransferData.setComments(comments);

            Transfer newTransfer = transferService.retransferAsset(retransferData);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Asset re-transferred successfully!",
                    "transferId", newTransfer.getTransferId(),
                    "sequence", newTransfer.getTransferSequence(),
                    "originalTransferId", newTransfer.getOriginalTransferId()
            ));

        } catch (Exception e) {
            log.error("Error re-transferring asset: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to re-transfer asset: " + e.getMessage()));
        }
    }

    // ============================================
    // FIX STATUS ENDPOINT
    // ============================================

    @PostMapping("/{transferId}/fix-status")
    @Operation(summary = "Fix transfer status (admin only)")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> fixTransferStatus(@PathVariable Long transferId) {
        try {
            Transfer transfer = transferRepository.findById(transferId)
                    .orElseThrow(() -> new RuntimeException("Transfer not found: " + transferId));

            log.info("🔧 Fixing status for transfer: {}", transferId);
            log.info("   Current isFullySigned: {}", transfer.getIsFullySigned());

            // Check if all required signatures are present
            boolean allSigned = true;
            List<String> missingSignatures = new ArrayList<>();

            if (transfer.getOldHandoverById() != null && transfer.getOldHandoverBySignedAt() == null) {
                allSigned = false;
                missingSignatures.add("Old Handover");
            }
            if (transfer.getOldReceivedById() != null && transfer.getOldReceivedBySignedAt() == null) {
                allSigned = false;
                missingSignatures.add("Old Received");
            }
            if (transfer.getNewHandoverById() != null && transfer.getNewHandoverBySignedAt() == null) {
                allSigned = false;
                missingSignatures.add("New Handover");
            }
            if (transfer.getNewReceivedById() != null && transfer.getNewReceivedBySignedAt() == null) {
                allSigned = false;
                missingSignatures.add("New Received");
            }
            if (transfer.getConfiguredById() != null && transfer.getConfiguredBySignedAt() == null) {
                allSigned = false;
                missingSignatures.add("Configured By");
            }
            if (transfer.getInfraRepresentativeId() != null && transfer.getInfraRepSignedAt() == null) {
                allSigned = false;
                missingSignatures.add("Infrastructure Representative");
            }
            if (transfer.getFinanceRepresentativeId() != null && transfer.getFinanceRepSignedAt() == null) {
                allSigned = false;
                missingSignatures.add("Finance Representative");
            }

            if (allSigned) {
                transfer.setIsFullySigned(true);
                Transfer saved = transferRepository.save(transfer);
                log.info("✅ Transfer {} status fixed! isFullySigned = {}", saved.getTransferId(), saved.getIsFullySigned());

                // Generate PDF if not already generated
                if (saved.getFullySignedPDF() == null) {
                    signingService.generateFullySignedPdf(transferId);
                }

                TransferDTO dto = TransferDTO.fromEntity(saved);

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Transfer status fixed! Marked as fully signed.",
                        "transfer", dto
                ));
            } else {
                log.info("❌ Transfer {} is not fully signed. Missing signatures: {}", transferId, missingSignatures);

                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Transfer is not fully signed. Missing signatures: " + String.join(", ", missingSignatures),
                        "transferId", transferId,
                        "missingSignatures", missingSignatures
                ));
            }
        } catch (Exception e) {
            log.error("Error fixing transfer status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fix transfer status: " + e.getMessage()));
        }
    }
}