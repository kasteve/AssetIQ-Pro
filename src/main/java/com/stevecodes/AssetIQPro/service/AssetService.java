package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.AssetDTO;
import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.AssetHistory;
import com.stevecodes.AssetIQPro.entity.Category;
import com.stevecodes.AssetIQPro.repository.AssetHistoryRepository;
import com.stevecodes.AssetIQPro.repository.AssetRepository;
import com.stevecodes.AssetIQPro.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetHistoryRepository historyRepository;
    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    private static final String UPLOAD_DIR = "./uploads/invoices/";

    // ============================================
    // CRUD Operations
    // ============================================

    @Transactional
    public Asset createAsset(Asset asset) {
        log.info("Creating asset with tag: {}", asset.getTag());

        if (assetRepository.existsByTag(asset.getTag())) {
            throw new IllegalArgumentException("Asset tag already exists: " + asset.getTag());
        }

        // Set warranty end date based on purchase date and warranty years
        if (asset.getPurchaseDate() != null && asset.getWarrantyYears() != null) {
            asset.setWarrantyEndDate(asset.getPurchaseDate().plusYears(asset.getWarrantyYears()));
        }

        // Set EOL notification days default
        if (asset.getEolNotificationDays() == null) {
            asset.setEolNotificationDays(30);
        }
        if (asset.getWarrantyNotificationDays() == null) {
            asset.setWarrantyNotificationDays(30);
        }

        Asset saved = assetRepository.save(asset);

        // Create history entry
        createHistory(saved.getAssetId(), AssetHistory.EVENT_PROCUREMENT,
                "Asset created with tag: " + saved.getTag(), null);

        auditService.logAction("ASSET_CREATED", "Asset created: " + saved.getTag(), null);

        return saved;
    }

    public AssetDTO getAssetById(Integer assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("Asset not found: " + assetId));
        return AssetDTO.fromEntity(asset);
    }

    public AssetDTO getAssetByTag(String tag) {
        Asset asset = assetRepository.findByTagIgnoreCase(tag)
                .orElseThrow(() -> new RuntimeException("Asset not found with tag: " + tag));
        return AssetDTO.fromEntity(asset);
    }

    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    public Page<AssetDTO> getAllAssets(Pageable pageable) {
        return assetRepository.findAll(pageable)
                .map(AssetDTO::fromEntity);
    }

    @Transactional
    public Asset updateAsset(Integer assetId, Asset updatedAsset) {
        log.info("Updating asset: {}", assetId);

        Asset existing = assetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("Asset not found: " + assetId));

        // Update fields
        existing.setName(updatedAsset.getName());
        existing.setSerialNumber(updatedAsset.getSerialNumber());
        existing.setCategory(updatedAsset.getCategory());
        existing.setSupplier(updatedAsset.getSupplier());
        existing.setLocation(updatedAsset.getLocation());
        existing.setPurchaseDate(updatedAsset.getPurchaseDate());
        existing.setPurchaseCost(updatedAsset.getPurchaseCost());
        existing.setStatus(updatedAsset.getStatus());

        // Update lifecycle fields
        if (updatedAsset.getWarrantyYears() != null) {
            existing.setWarrantyYears(updatedAsset.getWarrantyYears());
            if (existing.getPurchaseDate() != null) {
                existing.setWarrantyEndDate(existing.getPurchaseDate().plusYears(existing.getWarrantyYears()));
            }
        }
        if (updatedAsset.getEolDate() != null) {
            existing.setEolDate(updatedAsset.getEolDate());
        }
        if (updatedAsset.getEolNotificationDays() != null) {
            existing.setEolNotificationDays(updatedAsset.getEolNotificationDays());
        }
        if (updatedAsset.getWarrantyNotificationDays() != null) {
            existing.setWarrantyNotificationDays(updatedAsset.getWarrantyNotificationDays());
        }

        Asset saved = assetRepository.save(existing);

        createHistory(saved.getAssetId(), AssetHistory.EVENT_PROCUREMENT,
                "Asset updated: " + saved.getTag(), null);

        auditService.logAction("ASSET_UPDATED", "Asset updated: " + saved.getTag(), null);

        return saved;
    }

    @Transactional
    public void deleteAsset(Integer assetId) {
        log.info("Deleting asset: {}", assetId);

        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("Asset not found: " + assetId));

        createHistory(assetId, AssetHistory.EVENT_RETIREMENT,
                "Asset deleted: " + asset.getTag(), null);

        assetRepository.deleteById(assetId);

        auditService.logAction("ASSET_DELETED", "Asset deleted: " + asset.getTag(), null);
    }

    // ============================================
    // Search & Filter
    // ============================================

    public List<AssetDTO> searchAssets(String searchTerm) {
        return assetRepository.searchAssets(searchTerm).stream()
                .map(AssetDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<Asset> getAssetsByStatus(String status) {
        return assetRepository.findByStatus(Asset.AssetStatus.valueOf(status.toUpperCase()));
    }

    public List<Asset> getAssetsByCategory(Integer categoryId) {
        return assetRepository.findByCategoryId(categoryId);
    }

    // ============================================
    // Lifecycle Queries
    // ============================================

    public List<AssetDTO> getAssetsWithWarrantyExpiringSoon() {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(30);
        return assetRepository.findAssetsWithWarrantyExpiringWithinDays(today, threshold).stream()
                .map(AssetDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AssetDTO> getAssetsWithEOLSoon() {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(30);
        return assetRepository.findAssetsWithEOLWithinDays(today, threshold).stream()
                .map(AssetDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // ============================================
    // Asset History
    // ============================================

    public List<AssetHistory> getAssetHistory(Integer assetId) {
        return historyRepository.findByAssetIdOrderByEventDateDesc(assetId);
    }

    private void createHistory(Integer assetId, String eventType, String details, Long performedBy) {
        AssetHistory history = AssetHistory.create(assetId, eventType, performedBy, details);
        historyRepository.save(history);
    }

    // ============================================
    // File Upload
    // ============================================

    @Transactional
    public void uploadInvoice(Integer assetId, MultipartFile file) {
        try {
            Asset asset = assetRepository.findById(assetId)
                    .orElseThrow(() -> new RuntimeException("Asset not found: " + assetId));

            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(filename);

            // Save file
            Files.write(filePath, file.getBytes());

            // Update asset
            asset.setPurchaseInvoicePath(filePath.toString());
            assetRepository.save(asset);

            auditService.logAction("INVOICE_UPLOADED",
                    "Invoice uploaded for asset: " + asset.getTag(), null);

        } catch (IOException e) {
            log.error("Failed to upload invoice: {}", e.getMessage());
            throw new RuntimeException("Failed to upload invoice", e);
        }
    }

    // ============================================
    // Bulk Operations
    // ============================================

    @Transactional
    public List<Asset> createBulkAssets(List<Asset> assets) {
        log.info("Creating {} assets in bulk", assets.size());

        List<Asset> savedAssets = assets.stream()
                .map(this::createAsset)
                .collect(Collectors.toList());

        auditService.logAction("BULK_ASSETS_CREATED",
                "Created " + savedAssets.size() + " assets in bulk", null);

        return savedAssets;
    }

    // ============================================
    // Statistics
    // ============================================

    public long countTotalAssets() {
        return assetRepository.count();
    }

    public long countAssetsByStatus(Asset.AssetStatus status) {
        return assetRepository.countByStatus(status);
    }
}
