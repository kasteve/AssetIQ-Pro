package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Department;
import com.stevecodes.AssetIQPro.entity.Employee;
import com.stevecodes.AssetIQPro.service.DepartmentService;
import com.stevecodes.AssetIQPro.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/employees")
public class EmployeeViewController {

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;

    @GetMapping
    public String employees(Model model) {
        model.addAttribute("employees", employeeService.getAllEmployees());
        model.addAttribute("departments", departmentService.getAllDepartments());
        return "admin/employees";
    }

    @PostMapping
    public String createEmployee(
            @RequestParam String staffId,
            @RequestParam String firstName,
            @RequestParam String surName,
            @RequestParam String emailAddress,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String lineManagerId,
            // New Line Manager fields
            @RequestParam(required = false) String lmStaffId,
            @RequestParam(required = false) String lmFirstName,
            @RequestParam(required = false) String lmSurName,
            @RequestParam(required = false) String lmEmail,
            @RequestParam(required = false) String lmPhone,
            RedirectAttributes redirectAttributes) {
        try {
            // Check if staff ID already exists
            if (employeeService.getEmployeeByStaffId(staffId).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Staff ID '" + staffId + "' already exists!");
                return "redirect:/admin/employees";
            }

            Employee employee = new Employee();
            employee.setStaffId(staffId);
            employee.setFirstName(firstName);
            employee.setSurName(surName);
            employee.setEmailAddress(emailAddress);
            employee.setPhoneNumber(phoneNumber);

            if (departmentId != null) {
                Department dept = departmentService.getDepartmentById(departmentId).orElse(null);
                employee.setDepartment(dept);
            }

            // Handle Line Manager
            Long lineManagerIdLong = null;
            if (lineManagerId != null && !lineManagerId.isEmpty()) {
                if ("__NEW__".equals(lineManagerId)) {
                    // Create new line manager
                    Employee lineManager = new Employee();
                    lineManager.setStaffId(lmStaffId);
                    lineManager.setFirstName(lmFirstName);
                    lineManager.setSurName(lmSurName);
                    lineManager.setEmailAddress(lmEmail);
                    lineManager.setPhoneNumber(lmPhone);

                    if (departmentId != null) {
                        Department dept = departmentService.getDepartmentById(departmentId).orElse(null);
                        lineManager.setDepartment(dept);
                    }

                    Employee savedLm = employeeService.createEmployee(lineManager);
                    lineManagerIdLong = savedLm.getEmployeeId();
                    log.info("Created new line manager: {} ({})", savedLm.getFullName(), savedLm.getStaffId());
                } else {
                    lineManagerIdLong = Long.parseLong(lineManagerId);
                }

                if (lineManagerIdLong != null) {
                    Employee lineManager = employeeService.getEmployeeById(lineManagerIdLong).orElse(null);
                    employee.setLineManager(lineManager);
                }
            }

            employeeService.createEmployee(employee);
            redirectAttributes.addFlashAttribute("success", "Employee created successfully!");
        } catch (Exception e) {
            log.error("Error creating employee: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create employee: " + e.getMessage());
        }
        return "redirect:/admin/employees";
    }

    @PostMapping("/update")
    public String updateEmployee(
            @RequestParam Long employeeId,
            @RequestParam String staffId,
            @RequestParam String firstName,
            @RequestParam String surName,
            @RequestParam String emailAddress,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) Long lineManagerId,
            RedirectAttributes redirectAttributes) {
        try {
            employeeService.updateEmployee(employeeId, staffId, firstName, surName, emailAddress, phoneNumber, departmentId, lineManagerId);
            redirectAttributes.addFlashAttribute("success", "Employee updated successfully!");
        } catch (Exception e) {
            log.error("Error updating employee: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to update employee: " + e.getMessage());
        }
        return "redirect:/admin/employees";
    }

    @PostMapping("/{id}/delete")
    public String deleteEmployee(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            employeeService.deleteEmployee(id);
            redirectAttributes.addFlashAttribute("success", "Employee deleted successfully!");
        } catch (Exception e) {
            log.error("Error deleting employee: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to delete employee: " + e.getMessage());
        }
        return "redirect:/admin/employees";
    }
}