package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.StockItem;
import com.stevecodes.AssetIQPro.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/stock")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('MANAGE_INVENTORY')")
public class StockController {

    private final StockService stockService;

    // ============================================
    // Page
    // ============================================
    @GetMapping
    public String stockPage(Model model) {
        model.addAttribute("items", stockService.getAllStockItems());
        model.addAttribute("pageTitle", "Stock Management");
        model.addAttribute("currentPage", "stock");
        return "admin/stock";
    }

    // ============================================
    // Form-backed endpoints (plain HTML <form> submits -> redirect back to page)
    // ============================================
    @PostMapping("/api")
    public String create(@ModelAttribute StockItem item) {
        try {
            stockService.saveStockItem(item);
            return "redirect:/admin/stock?success=Item added successfully";
        } catch (Exception e) {
            return "redirect:/admin/stock?error=" + e.getMessage();
        }
    }

    @PostMapping("/api/{id}")
    public String update(@PathVariable Long id, @ModelAttribute StockItem item) {
        try {
            item.setId(id);
            stockService.saveStockItem(item);
            return "redirect:/admin/stock?success=Item updated successfully";
        } catch (Exception e) {
            return "redirect:/admin/stock?error=" + e.getMessage();
        }
    }

    @PostMapping("/api/{id}/deduct")
    public String deduct(@PathVariable Long id,
                         @RequestParam int amount,
                         @RequestParam(required = false) String reason) {
        try {
            stockService.deductStock(id, amount, reason);
            return "redirect:/admin/stock?success=Stock deducted successfully";
        } catch (Exception e) {
            return "redirect:/admin/stock?error=" + e.getMessage();
        }
    }

    @PostMapping("/api/{id}/restock")
    public String restock(@PathVariable Long id, @RequestParam int amount) {
        try {
            stockService.restock(id, amount);
            return "redirect:/admin/stock?success=Item restocked successfully";
        } catch (Exception e) {
            return "redirect:/admin/stock?error=" + e.getMessage();
        }
    }

    // ============================================
    // JSON API endpoints (used by fetch() calls in the page, e.g. delete)
    // ============================================
    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<List<StockItem>> getAll() {
        return ResponseEntity.ok(stockService.getAllStockItems());
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<StockItem> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(stockService.getStockItem(id));
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        stockService.deleteStockItem(id);
        return ResponseEntity.noContent().build();
    }
}