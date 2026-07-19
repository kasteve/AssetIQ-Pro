package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Category;
import com.stevecodes.AssetIQPro.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/categories")
public class CategoryViewController {

    private final CategoryService categoryService;

    @GetMapping
    public String categories(Model model) {
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/categories";
    }

    @PostMapping
    public String createCategory(@RequestParam String name, @RequestParam(required = false) String description,
                                 RedirectAttributes redirectAttributes) {
        try {
            Category category = new Category(name, description);
            categoryService.createCategory(category);
            redirectAttributes.addFlashAttribute("success", "Category created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create category: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @GetMapping("/{id}/delete")
    public String deleteCategory(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("success", "Category deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete category: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/edit")
    public String updateCategory(@PathVariable Integer id, @RequestParam String name,
                                 @RequestParam(required = false) String description,
                                 RedirectAttributes redirectAttributes) {
        try {
            categoryService.updateCategory(id, name, description);
            redirectAttributes.addFlashAttribute("success", "Category updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update category: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }
}