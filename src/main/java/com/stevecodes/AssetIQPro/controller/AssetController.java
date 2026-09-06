package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.AssetDTO;
import com.stevecodes.AssetIQPro.dto.BatchUploadResultDTO;
import com.stevecodes.AssetIQPro.dto.TransferDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.AssetHistory;
import com.stevecodes.AssetIQPro.security.Permissions;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.AssetCsvImportService;
import com.stevecodes.AssetIQPro.service.AssetService;
import com.stevecodes.AssetIQPro.service.AssetLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Tag(name = "Asset Management", description = "Asset management APIs")
public class AssetController {

    private final AssetService assetService;
    private final AssetLifecycleService lifecycleService;
    private final AssetCsvImportService assetCsvImportService;

    @PostMapping
    @Operation(summary = "Create a new asset")
    @PreAuthorize("hasAnyAuthority('ASSET_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> createAsset(@Valid @RequestBody Asset asset) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(assetService.createAsset(asset));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error creating asset: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating asset: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create asset: " + e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Get all assets with pagination")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Page<AssetDTO>> getAllAssets(Pageable pageable) {
        return ResponseEntity.ok(assetService.getAllAssets(pageable));
    }

    @GetMapping("/all")
    @Operation(summary = "Get all assets (no pagination)")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Asset>> getAllAssets() {
        return ResponseEntity.ok(assetService.getAllAssets());
    }

    @GetMapping("/{assetId}")
    @Operation(summary = "Get asset by ID")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getAssetById(@PathVariable Integer assetId) {
        try {
            return ResponseEntity.ok(assetService.getAssetById(assetId));
        } catch (Exception e) {
            log.error("Error fetching asset {}: {}", assetId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Asset not found: " + e.getMessage()));
        }
    }

    @GetMapping("/tag/{tag}")
    @Operation(summary = "Get asset by tag")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AssetDTO> getAssetByTag(@PathVariable String tag) {
        return ResponseEntity.ok(assetService.getAssetByTag(tag));
    }

    @PutMapping("/{assetId}")
    @Operation(summary = "Update asset")
    @PreAuthorize("hasAnyAuthority('ASSET_EDIT', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> updateAsset(@PathVariable Integer assetId, @Valid @RequestBody Asset asset) {
        try {
            return ResponseEntity.ok(assetService.updateAsset(assetId, asset));
        } catch (IllegalArgumentException e) {
            log.warn("Validation error updating asset: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating asset {}: {}", assetId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update asset: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{assetId}")
    @Operation(summary = "Delete asset")
    @PreAuthorize("hasAnyAuthority('DELETE_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> deleteAsset(@PathVariable Integer assetId) {
        try {
            assetService.deleteAsset(assetId);
            return ResponseEntity.ok(Map.of("message", "Asset deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting asset {}: {}", assetId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete asset: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    @Operation(summary = "Search assets")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<AssetDTO>> searchAssets(@RequestParam String searchTerm) {
        return ResponseEntity.ok(assetService.searchAssets(searchTerm));
    }

    @GetMapping("/by-status/{status}")
    @Operation(summary = "Get assets by status")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Asset>> getAssetsByStatus(@PathVariable String status) {
        return ResponseEntity.ok(assetService.getAssetsByStatus(status));
    }

    @GetMapping("/by-category/{categoryId}")
    @Operation(summary = "Get assets by category")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Asset>> getAssetsByCategory(@PathVariable Integer categoryId) {
        return ResponseEntity.ok(assetService.getAssetsByCategory(categoryId));
    }

    @GetMapping("/{assetId}/history")
    @Operation(summary = "Get asset history")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<AssetHistory>> getAssetHistory(@PathVariable Integer assetId) {
        return ResponseEntity.ok(assetService.getAssetHistory(assetId));
    }

    @GetMapping("/{assetId}/transfers")
    @Operation(summary = "Get asset transfer history")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'TRANSFER_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<TransferDTO>> getAssetTransfers(@PathVariable Integer assetId) {
        return ResponseEntity.ok(assetService.getAssetTransfers(assetId));
    }

    @PostMapping("/{assetId}/invoice")
    @Operation(summary = "Upload purchase invoice")
    @PreAuthorize("hasAnyAuthority('ASSET_EDIT', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> uploadInvoice(@PathVariable Integer assetId, @RequestParam("file") MultipartFile file) {
        try {
            assetService.uploadInvoice(assetId, file);
            return ResponseEntity.ok(Map.of("message", "Invoice uploaded successfully"));
        } catch (Exception e) {
            log.error("Error uploading invoice: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload invoice: " + e.getMessage()));
        }
    }

    @PutMapping("/{assetId}/lifecycle")
    @Operation(summary = "Update asset lifecycle (warranty, EOL)")
    @PreAuthorize("hasAnyAuthority('MANAGE_WARRANTY', 'MANAGE_EOL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> updateLifecycle(@PathVariable Integer assetId,
                                             @RequestParam(required = false) Integer warrantyYears,
                                             @RequestParam(required = false) String eolDate,
                                             @RequestParam(required = false) Integer eolNotificationDays,
                                             @RequestParam(required = false) Integer warrantyNotificationDays) {
        try {
            lifecycleService.updateAssetLifecycle(
                    assetId,
                    warrantyYears,
                    eolDate != null ? java.time.LocalDate.parse(eolDate) : null,
                    eolNotificationDays,
                    warrantyNotificationDays
            );
            return ResponseEntity.ok(Map.of("message", "Lifecycle updated successfully"));
        } catch (Exception e) {
            log.error("Error updating lifecycle: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update lifecycle: " + e.getMessage()));
        }
    }

    @GetMapping("/warranty-expiring")
    @Operation(summary = "Get assets with warranty expiring soon")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<AssetDTO>> getWarrantyExpiringSoon() {
        return ResponseEntity.ok(assetService.getAssetsWithWarrantyExpiringSoon());
    }

    @GetMapping("/eol-soon")
    @Operation(summary = "Get assets with EOL approaching")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<AssetDTO>> getEOLSoon() {
        return ResponseEntity.ok(assetService.getAssetsWithEOLSoon());
    }

    @PostMapping("/bulk")
    @Operation(summary = "Create multiple assets")
    @PreAuthorize("hasAnyAuthority('ASSET_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> createBulkAssets(@Valid @RequestBody List<Asset> assets) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(assetService.createBulkAssets(assets));
        } catch (Exception e) {
            log.error("Error creating bulk assets: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create assets: " + e.getMessage()));
        }
    }

    /**
     * Batch upload assets from CSV file
     */
    @PostMapping("/batch-upload")
    @Operation(summary = "Upload assets from CSV file")
    @PreAuthorize("hasAnyAuthority('ASSET_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> batchUploadAssets(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Please select a CSV file to upload"));
            }

            // Check file type
            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only CSV files are supported"));
            }

            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }

            BatchUploadResultDTO result = assetCsvImportService.importAssetsFromCsv(file, currentUser.getUserId());

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", !result.isHasErrors() || result.getSuccessfulRecords() > 0);
            response.put("totalRecords", result.getTotalRecords());
            response.put("successfulRecords", result.getSuccessfulRecords());
            response.put("failedRecords", result.getFailedRecords());
            response.put("hasErrors", result.isHasErrors());
            response.put("errors", result.getErrors());
            response.put("successMessages", result.getSuccessMessages());

            if (result.isHasErrors() && result.getSuccessfulRecords() == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in batch upload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process CSV upload: " + e.getMessage()));
        }
    }

    /**
     * Download CSV template for batch upload
     */
    @GetMapping("/batch-template")
    @Operation(summary = "Download CSV template for batch upload")
    public ResponseEntity<byte[]> downloadBatchTemplate() {
        try {
            String template = "tag,name,serialNumber,category,supplier,location,purchaseDate,purchaseCost,status,warrantyYears,warrantyEndDate,eolDate,warrantyNotificationDays,eolNotificationDays,department,lifespanYears\n" +
                    "AST-001,MacBook Pro,MN12345,Laptop,,,2024-01-15,2500.00,AVAILABLE,3,2027-01-15,2029-01-15,30,30,IT,5\n" +
                    "AST-002,Dell XPS 15,SN67890,Laptop,,,2024-02-01,1800.00,ASSIGNED,2,2026-02-01,2028-02-01,30,30,Finance,4\n" +
                    "AST-003,HP Monitor,HP-MON-001,Monitor,,,2023-12-01,350.00,AVAILABLE,2,2025-12-01,2027-12-01,30,30,IT,3\n";

            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=asset_batch_template.csv")
                    .body(template.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error generating CSV template: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}