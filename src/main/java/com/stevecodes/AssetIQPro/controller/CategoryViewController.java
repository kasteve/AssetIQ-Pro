package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Category;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
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
        log.info("Loading categories page");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("canEdit", currentUser.hasAnyPermission("CATEGORY_VIEW", "ADMIN"));
        model.addAttribute("canDelete", currentUser.hasAnyPermission("CATEGORY_VIEW", "ADMIN"));

        return "admin/categories";
    }

    @PostMapping
    public String createCategory(@RequestParam String name, @RequestParam(required = false) String description,
                                 RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            if (!currentUser.hasAnyPermission("CATEGORY_VIEW", "ADMIN")) {
                throw new AccessDeniedException("You don't have permission to create categories.");
            }

            Category category = new Category(name, description);
            categoryService.createCategory(category);
            redirectAttributes.addFlashAttribute("success", "Category created successfully!");
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create categories.");
        } catch (Exception e) {
            log.error("Error creating category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create category: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @GetMapping("/{id}/delete")
    public String deleteCategory(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            if (!currentUser.hasAnyPermission("CATEGORY_VIEW", "ADMIN")) {
                throw new AccessDeniedException("You don't have permission to delete categories.");
            }

            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("success", "Category deleted successfully!");
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to delete categories.");
        } catch (Exception e) {
            log.error("Error deleting category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to delete category: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/edit")
    public String updateCategory(@PathVariable Integer id, @RequestParam String name,
                                 @RequestParam(required = false) String description,
                                 RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            if (!currentUser.hasAnyPermission("CATEGORY_VIEW", "ADMIN")) {
                throw new AccessDeniedException("You don't have permission to update categories.");
            }

            categoryService.updateCategory(id, name, description);
            redirectAttributes.addFlashAttribute("success", "Category updated successfully!");
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to update categories.");
        } catch (Exception e) {
            log.error("Error updating category: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to update category: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }
}