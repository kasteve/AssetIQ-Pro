package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.Category;
import com.stevecodes.AssetIQPro.entity.Location;
import com.stevecodes.AssetIQPro.entity.Supplier;
import com.stevecodes.AssetIQPro.service.AssetService;
import com.stevecodes.AssetIQPro.service.CategoryService;
import com.stevecodes.AssetIQPro.service.LocationService;
import com.stevecodes.AssetIQPro.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/assets")
public class AssetViewController {

    private final AssetService assetService;
    private final CategoryService categoryService;
    private final LocationService locationService;
    private final SupplierService supplierService;

    @GetMapping
    public String assets(Model model) {
        log.info("Loading assets page");
        model.addAttribute("assets", assetService.getAllAssets());
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("locations", locationService.getAllLocations());
        model.addAttribute("suppliers", supplierService.getAllSuppliers());
        return "assets/list";
    }

    @PostMapping("/create")
    public String createAsset(
            @RequestParam String tag,
            @RequestParam String name,
            @RequestParam(required = false) String serialNumber,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String purchaseDate,
            @RequestParam(required = false) Double purchaseCost,
            @RequestParam(required = false) Integer supplierId,
            @RequestParam(required = false) Integer locationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer warrantyYears,
            @RequestParam(required = false) String warrantyEndDate,
            @RequestParam(required = false) String eolDate,
            @RequestParam(required = false) Integer warrantyNotificationDays,
            @RequestParam(required = false) Integer eolNotificationDays,
            @RequestParam(value = "invoice", required = false) MultipartFile file,
            RedirectAttributes redirectAttributes) {
        try {
            Asset asset = new Asset();
            asset.setTag(tag);
            asset.setName(name);
            asset.setSerialNumber(serialNumber);
            asset.setWarrantyYears(warrantyYears != null ? warrantyYears : 1);
            asset.setWarrantyNotificationDays(warrantyNotificationDays != null ? warrantyNotificationDays : 30);
            asset.setEolNotificationDays(eolNotificationDays != null ? eolNotificationDays : 30);

            if (categoryId != null) {
                Category category = categoryService.getCategoryById(categoryId);
                asset.setCategory(category);
            }
            if (supplierId != null) {
                Supplier supplier = supplierService.getSupplierById(supplierId);
                asset.setSupplier(supplier);
            }
            if (locationId != null) {
                Location location = locationService.getLocationById(locationId);
                asset.setLocation(location);
            }
            if (purchaseDate != null && !purchaseDate.isEmpty()) {
                asset.setPurchaseDate(LocalDate.parse(purchaseDate));
            }
            if (purchaseCost != null) {
                asset.setPurchaseCost(BigDecimal.valueOf(purchaseCost));
            }
            if (status != null && !status.isEmpty()) {
                asset.setStatus(Asset.AssetStatus.valueOf(status));
            }
            if (warrantyEndDate != null && !warrantyEndDate.isEmpty()) {
                asset.setWarrantyEndDate(LocalDate.parse(warrantyEndDate));
            }
            if (eolDate != null && !eolDate.isEmpty()) {
                asset.setEolDate(LocalDate.parse(eolDate));
            }

            Asset created = assetService.createAsset(asset);

            if (file != null && !file.isEmpty()) {
                assetService.uploadInvoice(created.getAssetId(), file);
            }

            redirectAttributes.addFlashAttribute("success", "Asset created successfully!");
            return "redirect:/assets?success=true";
        } catch (Exception e) {
            log.error("Error creating asset: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create asset: " + e.getMessage());
            return "redirect:/assets?error=true";
        }
    }
}