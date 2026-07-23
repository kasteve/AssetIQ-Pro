package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final CompanyService companyService;
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String assets(Model model) {
        log.info("Loading assets page");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("assets", assetService.getAllAssets());
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("locations", locationService.getAllLocations());
        model.addAttribute("suppliers", supplierService.getAllSuppliers());
        model.addAttribute("companies", companyService.getAllCompanies());
        model.addAttribute("employees", employeeService.getAllEmployees());
        model.addAttribute("departments", departmentService.getAllDepartments());
        model.addAttribute("canEdit", currentUser.hasAnyPermission("ASSET_EDIT", "EDIT_ASSETS", "ADMIN"));
        model.addAttribute("canDelete", currentUser.hasAnyPermission("DELETE_ASSETS", "ADMIN"));
        model.addAttribute("canCreate", currentUser.hasAnyPermission("ASSET_CREATE", "EDIT_ASSETS", "ADMIN"));

        return "assets/list";
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ASSET_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
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
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            Asset asset = new Asset();
            asset.setTag(tag);
            asset.setName(name);
            asset.setSerialNumber(serialNumber);
            asset.setWarrantyYears(warrantyYears != null ? warrantyYears : 1);
            asset.setWarrantyNotificationDays(warrantyNotificationDays != null ? warrantyNotificationDays : 30);
            asset.setEolNotificationDays(eolNotificationDays != null ? eolNotificationDays : 30);

            if (categoryId != null) {
                asset.setCategory(categoryService.getCategoryById(categoryId));
            }
            if (supplierId != null) {
                asset.setSupplier(supplierService.getSupplierById(supplierId));
            }
            if (locationId != null) {
                asset.setLocation(locationService.getLocationById(locationId));
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
            return "redirect:/assets";
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create assets.");
            return "redirect:/assets";
        } catch (Exception e) {
            log.error("Error creating asset: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create asset: " + e.getMessage());
            return "redirect:/assets";
        }
    }

    @PostMapping("/{id}/update")
    @PreAuthorize("hasAnyAuthority('ASSET_EDIT', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public String updateAsset(
            @PathVariable Integer id,
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
            RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            Asset asset = new Asset();
            asset.setAssetId(id);
            asset.setTag(tag);
            asset.setName(name);
            asset.setSerialNumber(serialNumber);
            asset.setWarrantyYears(warrantyYears != null ? warrantyYears : 1);
            asset.setWarrantyNotificationDays(warrantyNotificationDays != null ? warrantyNotificationDays : 30);
            asset.setEolNotificationDays(eolNotificationDays != null ? eolNotificationDays : 30);

            if (categoryId != null) {
                asset.setCategory(categoryService.getCategoryById(categoryId));
            }
            if (supplierId != null) {
                asset.setSupplier(supplierService.getSupplierById(supplierId));
            }
            if (locationId != null) {
                asset.setLocation(locationService.getLocationById(locationId));
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

            assetService.updateAsset(id, asset);
            redirectAttributes.addFlashAttribute("success", "Asset updated successfully!");
            return "redirect:/assets";
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to edit assets.");
            return "redirect:/assets";
        } catch (Exception e) {
            log.error("Error updating asset: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to update asset: " + e.getMessage());
            return "redirect:/assets";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAnyAuthority('DELETE_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public String deleteAsset(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            assetService.deleteAsset(id);
            redirectAttributes.addFlashAttribute("success", "Asset deleted successfully!");
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to delete assets.");
        } catch (Exception e) {
            log.error("Error deleting asset: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to delete asset: " + e.getMessage());
        }
        return "redirect:/assets";
    }
}