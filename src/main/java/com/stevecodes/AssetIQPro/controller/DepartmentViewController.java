package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Department;
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

    @GetMapping
    public String departments(Model model) {
        model.addAttribute("departments", departmentService.getAllDepartments());
        return "admin/departments";
    }

    @PostMapping
    public String createDepartment(@RequestParam String name, @RequestParam(required = false) Long managerId,
                                   RedirectAttributes redirectAttributes) {
        try {
            Department dept = new Department(name);
            departmentService.createDepartment(dept);
            redirectAttributes.addFlashAttribute("success", "Department created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create department: " + e.getMessage());
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