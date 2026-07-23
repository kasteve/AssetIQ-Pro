package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.StockItem;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/stock")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('MANAGE_INVENTORY', 'ADMIN', 'SUPER_ADMIN')")
public class StockController {

    private final StockService stockService;

    @GetMapping
    public String stockPage(Model model) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }
        model.addAttribute("items", stockService.getAllStockItems());
        model.addAttribute("pageTitle", "Stock Management");
        model.addAttribute("currentPage", "stock");
        model.addAttribute("canEdit", currentUser.hasAnyPermission("MANAGE_INVENTORY", "ADMIN"));
        model.addAttribute("canDelete", currentUser.hasAnyPermission("MANAGE_INVENTORY", "ADMIN"));
        return "admin/stock";
    }

    @PostMapping("/api")
    @PreAuthorize("hasAnyAuthority('MANAGE_INVENTORY', 'ADMIN', 'SUPER_ADMIN')")
    public String create(@ModelAttribute StockItem item) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return "redirect:/login?error=You must be logged in";
            }
            stockService.saveStockItem(item);
            return "redirect:/admin/stock?success=Item added successfully";
        } catch (AccessDeniedException e) {
            return "redirect:/admin/stock?error=You don't have permission to create stock items";
        } catch (Exception e) {
            return "redirect:/admin/stock?error=" + e.getMessage();
        }
    }

    @PostMapping("/api/{id}")
    @PreAuthorize("hasAnyAuthority('MANAGE_INVENTORY', 'ADMIN', 'SUPER_ADMIN')")
    public String update(@PathVariable Long id, @ModelAttribute StockItem item) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return "redirect:/login?error=You must be logged in";
            }
            item.setId(id);
            stockService.saveStockItem(item);
            return "redirect:/admin/stock?success=Item updated successfully";
        } catch (AccessDeniedException e) {
            return "redirect:/admin/stock?error=You don't have permission to update stock items";
        } catch (Exception e) {
            return "redirect:/admin/stock?error=" + e.getMessage();
        }
    }

    @PostMapping("/api/{id}/deduct")
    @PreAuthorize("hasAnyAuthority('MANAGE_INVENTORY', 'ADMIN', 'SUPER_ADMIN')")
    public String deduct(@PathVariable Long id,
                         @RequestParam int amount,
                         @RequestParam(required = false) String reason) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return "redirect:/login?error=You must be logged in";
            }
            stockService.deductStock(id, amount, reason);
            return "redirect:/admin/stock?success=Stock deducted successfully";
        } catch (AccessDeniedException e) {
            return "redirect:/admin/stock?error=You don't have permission to deduct stock";
        } catch (Exception e) {
            return "redirect:/admin/stock?error=" + e.getMessage();
        }
    }

    @PostMapping("/api/{id}/restock")
    @PreAuthorize("hasAnyAuthority('MANAGE_INVENTORY', 'ADMIN', 'SUPER_ADMIN')")
    public String restock(@PathVariable Long id, @RequestParam int amount) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return "redirect:/login?error=You must be logged in";
            }
            stockService.restock(id, amount);
            return "redirect:/admin/stock?success=Item restocked successfully";
        } catch (AccessDeniedException e) {
            return "redirect:/admin/stock?error=You don't have permission to restock";
        } catch (Exception e) {
            return "redirect:/admin/stock?error=" + e.getMessage();
        }
    }

    @GetMapping("/api")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('MANAGE_INVENTORY', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<StockItem>> getAll() {
        return ResponseEntity.ok(stockService.getAllStockItems());
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('MANAGE_INVENTORY', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<StockItem> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(stockService.getStockItem(id));
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('MANAGE_INVENTORY', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(401).build();
            }
            stockService.deleteStockItem(id);
            return ResponseEntity.noContent().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).build();
        }
    }
}