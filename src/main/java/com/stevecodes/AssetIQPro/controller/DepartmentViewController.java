package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Department;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/departments")
public class DepartmentViewController {

    private final DepartmentService departmentService;
    private final AppUserRepository appUserRepository;

    @GetMapping
    public String departments(Model model) {
        model.addAttribute("departments", departmentService.getAllDepartments());
        model.addAttribute("users", appUserRepository.findAll());
        return "admin/departments";
    }

    @PostMapping
    public String createDepartment(@RequestParam String name,
                                   @RequestParam(required = false) Long managerId,
                                   RedirectAttributes redirectAttributes) {
        try {
            Department dept = new Department(name);
            if (managerId != null) {
                appUserRepository.findById(managerId)
                        .ifPresent(dept::setManager);
            }
            departmentService.createDepartment(dept);
            redirectAttributes.addFlashAttribute("success", "Department created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create department: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/{id}/edit")
    public String updateDepartment(@PathVariable Integer id,
                                   @RequestParam String name,
                                   @RequestParam(required = false) Long managerId,
                                   RedirectAttributes redirectAttributes) {
        try {
            log.info("Updating department ID: {}, Name: {}, ManagerId: {}", id, name, managerId);
            departmentService.updateDepartment(id, name, managerId);
            redirectAttributes.addFlashAttribute("success", "Department updated successfully!");
        } catch (Exception e) {
            log.error("Error updating department: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to update department: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @GetMapping("/{id}/delete")
    public String deleteDepartment(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            departmentService.deleteDepartment(id);
            redirectAttributes.addFlashAttribute("success", "Department deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete department: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }
}