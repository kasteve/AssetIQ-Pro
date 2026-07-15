package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.UserDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.AuditService;
import com.stevecodes.AssetIQPro.service.DepartmentService;
import com.stevecodes.AssetIQPro.service.EmailService;
import com.stevecodes.AssetIQPro.service.EmployeeService;
import com.stevecodes.AssetIQPro.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class UserViewController {

    private final AppUserService userService;
    private final PermissionService permissionService;
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;

    @GetMapping
    public String users(Model model) {
        log.info("Loading user management page");

        List<AppUser> users = userService.getAllUsers();

        long totalUsers = users.size();
        long activeUsers = users.stream().filter(AppUser::isActive).count();
        long blockedUsers = users.stream().filter(AppUser::isBlocked).count();
        long pendingPasswordChange = users.stream().filter(AppUser::isMustChangePassword).count();

        model.addAttribute("users", users);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("blockedUsers", blockedUsers);
        model.addAttribute("pendingPasswordChange", pendingPasswordChange);
        model.addAttribute("allPermissions", permissionService.getAllPermissions());
        model.addAttribute("departments", departmentService.getAllDepartments());
        model.addAttribute("employees", employeeService.getAllEmployees());
        model.addAttribute("roles", List.of("EMPLOYEE", "DRIVER", "INFRA", "FINANCE", "MANAGER", "ADMIN", "SUPERADMIN"));

        return "admin/users";
    }

    @PostMapping("/create")
    public String createUser(@ModelAttribute UserDTO userDTO, RedirectAttributes redirectAttributes) {
        try {
            log.info("Creating user: {}", userDTO.getUsername());
            userService.createUser(userDTO);
            redirectAttributes.addFlashAttribute("success", "User created successfully! Welcome email sent.");
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to create user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/update")
    public String updateUser(@ModelAttribute UserDTO userDTO, RedirectAttributes redirectAttributes) {
        try {
            log.info("Updating user: {}", userDTO.getUserId());
            userService.updateUser(userDTO.getUserId(), userDTO);
            redirectAttributes.addFlashAttribute("success", "User updated successfully!");
        } catch (Exception e) {
            log.error("Error updating user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to update user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/reset-password")
    @ResponseBody
    public ResponseEntity<?> resetPassword(@PathVariable Long userId) {
        try {
            log.info("Resetting password for user: {}", userId);
            userService.resetPassword(userId);
            return ResponseEntity.ok().body(Map.of("success", true, "message", "Password reset email sent"));
        } catch (Exception e) {
            log.error("Error resetting password: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{userId}/toggle")
    public String toggleUser(@PathVariable Long userId, RedirectAttributes redirectAttributes) {
        try {
            userService.toggleUserStatus(userId);
            redirectAttributes.addFlashAttribute("success", "User status toggled successfully!");
        } catch (Exception e) {
            log.error("Error toggling user status: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to toggle user status: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/delete")
    public String deleteUser(@PathVariable Long userId, RedirectAttributes redirectAttributes) {
        try {
            userService.deleteUser(userId);
            redirectAttributes.addFlashAttribute("success", "User deleted successfully!");
        } catch (Exception e) {
            log.error("Error deleting user: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to delete user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }
}