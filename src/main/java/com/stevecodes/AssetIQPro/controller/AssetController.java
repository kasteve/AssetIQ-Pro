package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.AssetDTO;
import com.stevecodes.AssetIQPro.dto.TransferDTO;
import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.AssetHistory;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Tag(name = "Asset Management", description = "Asset management APIs")
public class AssetController {

    private final AssetService assetService;
    private final AssetLifecycleService lifecycleService;

    @PostMapping
    @Operation(summary = "Create a new asset")
    @PreAuthorize("hasAnyAuthority('EDIT_ASSETS', 'ADMIN')")
    public ResponseEntity<Asset> createAsset(@Valid @RequestBody Asset asset) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assetService.createAsset(asset));
    }

    @GetMapping
    @Operation(summary = "Get all assets with pagination")
    public ResponseEntity<Page<AssetDTO>> getAllAssets(Pageable pageable) {
        return ResponseEntity.ok(assetService.getAllAssets(pageable));
    }

    @GetMapping("/all")
    @Operation(summary = "Get all assets (no pagination)")
    public ResponseEntity<List<Asset>> getAllAssets() {
        return ResponseEntity.ok(assetService.getAllAssets());
    }

    @GetMapping("/{assetId}")
    @Operation(summary = "Get asset by ID")
    public ResponseEntity<AssetDTO> getAssetById(@PathVariable Integer assetId) {
        return ResponseEntity.ok(assetService.getAssetById(assetId));
    }

    @GetMapping("/tag/{tag}")
    @Operation(summary = "Get asset by tag")
    public ResponseEntity<AssetDTO> getAssetByTag(@PathVariable String tag) {
        return ResponseEntity.ok(assetService.getAssetByTag(tag));
    }

    @PutMapping("/{assetId}")
    @Operation(summary = "Update asset")
    @PreAuthorize("hasAnyAuthority('EDIT_ASSETS', 'ADMIN')")
    public ResponseEntity<Asset> updateAsset(@PathVariable Integer assetId,
                                             @Valid @RequestBody Asset asset) {
        return ResponseEntity.ok(assetService.updateAsset(assetId, asset));
    }

    @DeleteMapping("/{assetId}")
    @Operation(summary = "Delete asset")
    @PreAuthorize("hasAnyAuthority('DELETE_ASSETS', 'ADMIN')")
    public ResponseEntity<Void> deleteAsset(@PathVariable Integer assetId) {
        assetService.deleteAsset(assetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search assets")
    public ResponseEntity<List<AssetDTO>> searchAssets(@RequestParam String searchTerm) {
        return ResponseEntity.ok(assetService.searchAssets(searchTerm));
    }

    @GetMapping("/by-status/{status}")
    @Operation(summary = "Get assets by status")
    public ResponseEntity<List<Asset>> getAssetsByStatus(@PathVariable String status) {
        return ResponseEntity.ok(assetService.getAssetsByStatus(status));
    }

    @GetMapping("/by-category/{categoryId}")
    @Operation(summary = "Get assets by category")
    public ResponseEntity<List<Asset>> getAssetsByCategory(@PathVariable Integer categoryId) {
        return ResponseEntity.ok(assetService.getAssetsByCategory(categoryId));
    }

    @GetMapping("/{assetId}/history")
    @Operation(summary = "Get asset history")
    public ResponseEntity<List<AssetHistory>> getAssetHistory(@PathVariable Integer assetId) {
        return ResponseEntity.ok(assetService.getAssetHistory(assetId));
    }

    @GetMapping("/{assetId}/transfers")
    @Operation(summary = "Get asset transfer history")
    public ResponseEntity<List<TransferDTO>> getAssetTransfers(@PathVariable Integer assetId) {
        return ResponseEntity.ok(assetService.getAssetTransfers(assetId));
    }

    @PostMapping("/{assetId}/invoice")
    @Operation(summary = "Upload purchase invoice")
    @PreAuthorize("hasAnyAuthority('EDIT_ASSETS', 'ADMIN')")
    public ResponseEntity<Void> uploadInvoice(@PathVariable Integer assetId,
                                              @RequestParam("file") MultipartFile file) {
        assetService.uploadInvoice(assetId, file);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{assetId}/lifecycle")
    @Operation(summary = "Update asset lifecycle (warranty, EOL)")
    @PreAuthorize("hasAnyAuthority('MANAGE_WARRANTY', 'MANAGE_EOL', 'ADMIN')")
    public ResponseEntity<Void> updateLifecycle(@PathVariable Integer assetId,
                                                @RequestParam(required = false) Integer warrantyYears,
                                                @RequestParam(required = false) String eolDate,
                                                @RequestParam(required = false) Integer eolNotificationDays,
                                                @RequestParam(required = false) Integer warrantyNotificationDays) {
        lifecycleService.updateAssetLifecycle(
                assetId,
                warrantyYears,
                eolDate != null ? java.time.LocalDate.parse(eolDate) : null,
                eolNotificationDays,
                warrantyNotificationDays
        );
        return ResponseEntity.ok().build();
    }

    @GetMapping("/warranty-expiring")
    @Operation(summary = "Get assets with warranty expiring soon")
    public ResponseEntity<List<AssetDTO>> getWarrantyExpiringSoon() {
        return ResponseEntity.ok(assetService.getAssetsWithWarrantyExpiringSoon());
    }

    @GetMapping("/eol-soon")
    @Operation(summary = "Get assets with EOL approaching")
    public ResponseEntity<List<AssetDTO>> getEOLSoon() {
        return ResponseEntity.ok(assetService.getAssetsWithEOLSoon());
    }

    @PostMapping("/bulk")
    @Operation(summary = "Create multiple assets")
    @PreAuthorize("hasAnyAuthority('EDIT_ASSETS', 'ADMIN')")
    public ResponseEntity<List<Asset>> createBulkAssets(@Valid @RequestBody List<Asset> assets) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assetService.createBulkAssets(assets));
    }
}