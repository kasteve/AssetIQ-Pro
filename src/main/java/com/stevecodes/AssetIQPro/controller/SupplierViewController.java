package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Supplier;
import com.stevecodes.AssetIQPro.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/suppliers")
public class SupplierViewController {

    private final SupplierService supplierService;

    @GetMapping
    public String suppliers(Model model) {
        model.addAttribute("suppliers", supplierService.getAllSuppliers());
        return "admin/suppliers";
    }

    @PostMapping
    public String createSupplier(@RequestParam String name, @RequestParam(required = false) String contact,
                                 @RequestParam(required = false) String email, @RequestParam(required = false) String phone,
                                 RedirectAttributes redirectAttributes) {
        try {
            Supplier supplier = new Supplier(name);
            supplier.setContact(contact);
            supplier.setEmail(email);
            supplier.setPhone(phone);
            supplierService.createSupplier(supplier);
            redirectAttributes.addFlashAttribute("success", "Supplier created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create supplier: " + e.getMessage());
        }
        return "redirect:/admin/suppliers";
    }

    @GetMapping("/{id}/delete")
    public String deleteSupplier(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            supplierService.deleteSupplier(id);
            redirectAttributes.addFlashAttribute("success", "Supplier deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete supplier: " + e.getMessage());
        }
        return "redirect:/admin/suppliers";
    }
}