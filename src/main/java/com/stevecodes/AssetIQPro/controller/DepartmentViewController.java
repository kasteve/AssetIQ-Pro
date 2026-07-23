package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Department;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.DepartmentService;
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
@RequestMapping("/admin/departments")
@PreAuthorize("hasAnyAuthority('DEPARTMENT_VIEW', 'ADMIN', 'SUPER_ADMIN')")
public class DepartmentViewController {

    private final DepartmentService departmentService;
    private final AppUserRepository appUserRepository;

    @GetMapping
    public String departments(Model model) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }
        model.addAttribute("departments", departmentService.getAllDepartments());
        model.addAttribute("users", appUserRepository.findAll());
        model.addAttribute("canEdit", currentUser.hasAnyPermission("DEPARTMENT_VIEW", "ADMIN"));
        model.addAttribute("canDelete", currentUser.hasAnyPermission("DEPARTMENT_VIEW", "ADMIN"));
        return "admin/departments";
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('DEPARTMENT_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String createDepartment(@RequestParam String name,
                                   @RequestParam(required = false) Long managerId,
                                   RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            Department dept = new Department(name);
            if (managerId != null) {
                appUserRepository.findById(managerId)
                        .ifPresent(dept::setManager);
            }
            departmentService.createDepartment(dept);
            redirectAttributes.addFlashAttribute("success", "Department created successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create departments.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create department: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyAuthority('DEPARTMENT_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String updateDepartment(@PathVariable Integer id,
                                   @RequestParam String name,
                                   @RequestParam(required = false) Long managerId,
                                   RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("Updating department ID: {}, Name: {}, ManagerId: {}", id, name, managerId);
            departmentService.updateDepartment(id, name, managerId);
            redirectAttributes.addFlashAttribute("success", "Department updated successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to update departments.");
        } catch (Exception e) {
            log.error("Error updating department: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to update department: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @GetMapping("/{id}/delete")
    @PreAuthorize("hasAnyAuthority('DEPARTMENT_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String deleteDepartment(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            departmentService.deleteDepartment(id);
            redirectAttributes.addFlashAttribute("success", "Department deleted successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to delete departments.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete department: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }
}