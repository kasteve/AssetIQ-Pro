package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Supplier;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/suppliers")
@PreAuthorize("hasAnyAuthority('SUPPLIER_VIEW', 'ADMIN', 'SUPER_ADMIN')")
public class SupplierViewController {

    private final SupplierService supplierService;

    @GetMapping
    public String suppliers(Model model) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }
        model.addAttribute("suppliers", supplierService.getAllSuppliers());
        model.addAttribute("canEdit", currentUser.hasAnyPermission("SUPPLIER_VIEW", "ADMIN"));
        model.addAttribute("canDelete", currentUser.hasAnyPermission("SUPPLIER_VIEW", "ADMIN"));
        return "admin/suppliers";
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SUPPLIER_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String createSupplier(@RequestParam String name, @RequestParam(required = false) String contact,
                                 @RequestParam(required = false) String email, @RequestParam(required = false) String phone,
                                 RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }
            Supplier supplier = new Supplier(name);
            supplier.setContact(contact);
            supplier.setEmail(email);
            supplier.setPhone(phone);
            supplierService.createSupplier(supplier);
            redirectAttributes.addFlashAttribute("success", "Supplier created successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create suppliers.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create supplier: " + e.getMessage());
        }
        return "redirect:/admin/suppliers";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyAuthority('SUPPLIER_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String updateSupplier(@PathVariable Integer id, @RequestParam String name,
                                 @RequestParam(required = false) String contact,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String phone,
                                 RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }
            supplierService.updateSupplier(id, name, contact, email, phone);
            redirectAttributes.addFlashAttribute("success", "Supplier updated successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to update suppliers.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update supplier: " + e.getMessage());
        }
        return "redirect:/admin/suppliers";
    }

    @GetMapping("/{id}/delete")
    @PreAuthorize("hasAnyAuthority('SUPPLIER_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String deleteSupplier(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }
            supplierService.deleteSupplier(id);
            redirectAttributes.addFlashAttribute("success", "Supplier deleted successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to delete suppliers.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete supplier: " + e.getMessage());
        }
        return "redirect:/admin/suppliers";
    }
}