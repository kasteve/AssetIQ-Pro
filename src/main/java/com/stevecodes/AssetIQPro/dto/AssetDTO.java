package com.stevecodes.AssetIQPro.dto;

import com.stevecodes.AssetIQPro.entity.Asset;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class AssetDTO {

    private Integer assetId;
    private String tag;
    private String name;
    private String serialNumber;
    private Integer categoryId;
    private String categoryName;
    private Integer supplierId;
    private String supplierName;
    private Integer locationId;
    private String locationName;
    private LocalDate purchaseDate;
    private BigDecimal purchaseCost;
    private LocalDate warrantyExpiry;
    private String status;

    // Lifecycle
    private Integer warrantyYears;
    private LocalDate eolDate;
    private LocalDate warrantyEndDate;
    private String purchaseInvoicePath;
    private Integer eolNotificationDays;
    private Integer warrantyNotificationDays;

    private LocalDateTime createdAt;
    private Integer daysUntilWarrantyExpiry;
    private Integer daysUntilEOL;
    private boolean warrantyExpiringSoon;
    private boolean eolSoon;

    public static AssetDTO fromEntity(Asset asset) {
        AssetDTO dto = new AssetDTO();
        dto.setAssetId(asset.getAssetId());
        dto.setTag(asset.getTag());
        dto.setName(asset.getName());
        dto.setSerialNumber(asset.getSerialNumber());
        dto.setPurchaseDate(asset.getPurchaseDate());
        dto.setPurchaseCost(asset.getPurchaseCost());
        dto.setWarrantyExpiry(asset.getWarrantyExpiry());
        dto.setStatus(asset.getStatus().name());
        dto.setWarrantyYears(asset.getWarrantyYears());
        dto.setEolDate(asset.getEolDate());
        dto.setWarrantyEndDate(asset.getWarrantyEndDate());
        dto.setPurchaseInvoicePath(asset.getPurchaseInvoicePath());
        dto.setEolNotificationDays(asset.getEolNotificationDays());
        dto.setWarrantyNotificationDays(asset.getWarrantyNotificationDays());
        dto.setCreatedAt(asset.getCreatedAt());
        dto.setDaysUntilWarrantyExpiry(asset.getDaysUntilWarrantyExpiry());
        dto.setDaysUntilEOL(asset.getDaysUntilEOL());
        dto.setWarrantyExpiringSoon(asset.isWarrantyExpiringSoon());
        dto.setEolSoon(asset.isEOLSoon());

        if (asset.getCategory() != null) {
            dto.setCategoryId(asset.getCategory().getCategoryId());
            dto.setCategoryName(asset.getCategory().getName());
        }

        if (asset.getSupplier() != null) {
            dto.setSupplierId(asset.getSupplier().getSupplierId());
            dto.setSupplierName(asset.getSupplier().getName());
        }

        if (asset.getLocation() != null) {
            dto.setLocationId(asset.getLocation().getLocationId());
            dto.setLocationName(asset.getLocation().getName());
        }

        return dto;
    }
}