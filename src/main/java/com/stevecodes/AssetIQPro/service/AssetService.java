package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.AssetDTO;
import com.stevecodes.AssetIQPro.dto.BatchUploadResultDTO;
import com.stevecodes.AssetIQPro.dto.TransferDTO;
import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.AssetHistory;
import com.stevecodes.AssetIQPro.entity.Category;
import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.repository.AssetHistoryRepository;
import com.stevecodes.AssetIQPro.repository.AssetRepository;
import com.stevecodes.AssetIQPro.repository.CategoryRepository;
import com.stevecodes.AssetIQPro.repository.TransferRepository;
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
    private final TransferRepository transferRepository;
    private final AuditService auditService;
    private final AssetCsvImportService assetCsvImportService;

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

        // Auto-calc warranty end date from purchase date + warranty years
        if (asset.getPurchaseDate() != null && asset.getWarrantyYears() != null) {
            asset.setWarrantyEndDate(asset.getPurchaseDate().plusYears(asset.getWarrantyYears()));
        }

        // NEW: Auto-calc EOL date from purchase date + lifespan years
        if (asset.getPurchaseDate() != null && asset.getLifespanYears() != null) {
            asset.setEolDate(asset.getPurchaseDate().plusYears(asset.getLifespanYears()));
        }

        if (asset.getEolNotificationDays() == null) {
            asset.setEolNotificationDays(30);
        }
        if (asset.getWarrantyNotificationDays() == null) {
            asset.setWarrantyNotificationDays(30);
        }

        Asset saved = assetRepository.save(asset);

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

    public Asset getAssetEntityById(Integer assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("Asset not found: " + assetId));
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

    /**
     * Import assets from CSV file
     */
    @Transactional
    public BatchUploadResultDTO importAssetsFromCsv(MultipartFile file, Long userId) {
        // Delegate to AssetCsvImportService
        return assetCsvImportService.importAssetsFromCsv(file, userId);
    }

    @Transactional
    public Asset updateAsset(Integer assetId, Asset updatedAsset) {
        log.info("Updating asset: {}", assetId);

        Asset existing = assetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("Asset not found: " + assetId));

        existing.setName(updatedAsset.getName());
        existing.setSerialNumber(updatedAsset.getSerialNumber());
        existing.setCategory(updatedAsset.getCategory());
        existing.setSupplier(updatedAsset.getSupplier());
        existing.setLocation(updatedAsset.getLocation());
        existing.setPurchaseDate(updatedAsset.getPurchaseDate());
        existing.setPurchaseCost(updatedAsset.getPurchaseCost());
        existing.setStatus(updatedAsset.getStatus());

        if (updatedAsset.getWarrantyYears() != null) {
            existing.setWarrantyYears(updatedAsset.getWarrantyYears());
            if (existing.getPurchaseDate() != null) {
                existing.setWarrantyEndDate(existing.getPurchaseDate().plusYears(existing.getWarrantyYears()));
            }
        }

        // NEW: recalc EOL date whenever lifespan years is supplied
        if (updatedAsset.getLifespanYears() != null) {
            existing.setLifespanYears(updatedAsset.getLifespanYears());
            if (existing.getPurchaseDate() != null) {
                existing.setEolDate(existing.getPurchaseDate().plusYears(existing.getLifespanYears()));
            }
        } else if (updatedAsset.getEolDate() != null) {
            // Fallback: allow direct manual override when lifespan years isn't sent
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
    // Department-Based Asset Retrieval
    // ============================================

    public List<Asset> getAssetsByDepartment(Integer departmentId) {
        log.info("Getting assets for department ID: {}", departmentId);
        return assetRepository.findByDepartmentId(departmentId);
    }

    public List<Asset> getAssetsByDepartmentName(String departmentName) {
        log.info("Getting assets for department: {}", departmentName);
        return assetRepository.findByDepartment(departmentName);
    }

    public List<Asset> getAssetsByEmployeeDepartment(Long employeeId) {
        log.info("Getting assets for employee department: {}", employeeId);
        return assetRepository.findAssetsByEmployeeDepartment(employeeId);
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
    // Transfer History
    // ============================================

    public List<TransferDTO> getAssetTransfers(Integer assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("Asset not found: " + assetId));

        List<Transfer> transfers = transferRepository.findByAssetTag(asset.getTag());
        return transfers.stream().map(this::convertToTransferDTO).collect(Collectors.toList());
    }

    private TransferDTO convertToTransferDTO(Transfer transfer) {
        TransferDTO dto = new TransferDTO();
        dto.setTransferId(transfer.getTransferId());
        dto.setAssetTag(transfer.getAssetTag());
        dto.setSerialNumber(transfer.getSerialNumber());
        dto.setTransferDate(transfer.getTransferDate());
        dto.setOldDepartmentName(transfer.getOldDepartmentName());
        dto.setNewDepartmentName(transfer.getNewDepartmentName());
        dto.setIsFullySigned(transfer.getIsFullySigned());
        dto.setOldDepartmentId(transfer.getOldDepartmentId());
        dto.setNewDepartmentId(transfer.getNewDepartmentId());
        return dto;
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

            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(filename);

            Files.write(filePath, file.getBytes());

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