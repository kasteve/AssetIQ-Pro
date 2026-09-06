package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Department;
import com.stevecodes.AssetIQPro.entity.Employee;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.dto.UserDTO;
import com.stevecodes.AssetIQPro.exception.UserAlreadyExistsException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasAnyAuthority('USER_VIEW', 'CREATE_USERS', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
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
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

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
        model.addAttribute("canEdit", currentUser.hasAnyPermission("USER_EDIT", "MANAGE_USERS", "ADMIN"));
        model.addAttribute("canDelete", currentUser.hasAnyPermission("USER_EDIT", "MANAGE_USERS", "ADMIN"));
        model.addAttribute("canCreate", currentUser.hasAnyPermission("CREATE_USERS", "MANAGE_USERS", "ADMIN"));
        model.addAttribute("canResetPassword", currentUser.hasAnyPermission("RESET_PASSWORDS", "MANAGE_USERS", "ADMIN"));
        model.addAttribute("canManageRoles", currentUser.hasAnyPermission("MANAGE_ROLES", "ADMIN"));

        return "admin/users";
    }

    /**
     * ✅ UPDATED: Create user with support for new line manager
     */
    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('CREATE_USERS', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public String createUser(
            @RequestParam String staffId,
            @RequestParam String username,
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String lineManagerId,
            @RequestParam String role,
            @RequestParam(required = false) List<String> permissions,
            // ✅ NEW: Line Manager fields for creating new line manager
            @RequestParam(required = false) String lmStaffId,
            @RequestParam(required = false) String lmFirstName,
            @RequestParam(required = false) String lmSurName,
            @RequestParam(required = false) String lmEmail,
            @RequestParam(required = false) String lmPhone,
            @RequestParam(required = false) Integer lmDepartmentId,
            RedirectAttributes redirectAttributes) {

        log.info("=== CREATE USER START ===");
        log.info("Creating user: {}", username);
        log.info("Role: {}", role);
        log.info("Line Manager ID: {}", lineManagerId);

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
            return "redirect:/login";
        }

        if (!currentUser.hasAnyPermission("CREATE_USERS", "MANAGE_USERS", "ADMIN")) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create users.");
            return "redirect:/admin/users";
        }

        try {
            UserDTO userDTO = new UserDTO();
            userDTO.setStaffId(staffId);
            userDTO.setUsername(username);
            userDTO.setFullName(fullName);
            userDTO.setEmail(email);
            userDTO.setPhoneNumber(phoneNumber);
            userDTO.setDepartmentId(departmentId);
            userDTO.setRole(role);
            userDTO.setActive(true);
            userDTO.setBlocked(false);

            // ✅ Handle permissions
            if (permissions != null && !permissions.isEmpty()) {
                userDTO.setPermissions(permissions);
            }

            // ✅ Handle Line Manager - Check if we need to create a new one
            Long lineManagerEmployeeId = null;
            if (lineManagerId != null && !lineManagerId.isEmpty()) {
                if ("__NEW__".equals(lineManagerId)) {
                    // Create new line manager employee
                    log.info("Creating new line manager: {} {}", lmFirstName, lmSurName);

                    // Validate required fields for new line manager
                    if (lmStaffId == null || lmStaffId.isEmpty()) {
                        redirectAttributes.addFlashAttribute("error", "Line Manager Staff ID is required.");
                        return "redirect:/admin/users";
                    }
                    if (lmFirstName == null || lmFirstName.isEmpty()) {
                        redirectAttributes.addFlashAttribute("error", "Line Manager First Name is required.");
                        return "redirect:/admin/users";
                    }
                    if (lmSurName == null || lmSurName.isEmpty()) {
                        redirectAttributes.addFlashAttribute("error", "Line Manager Surname is required.");
                        return "redirect:/admin/users";
                    }
                    if (lmEmail == null || lmEmail.isEmpty()) {
                        redirectAttributes.addFlashAttribute("error", "Line Manager Email is required.");
                        return "redirect:/admin/users";
                    }

                    // Check if line manager already exists
                    if (employeeService.getEmployeeByStaffId(lmStaffId).isPresent()) {
                        redirectAttributes.addFlashAttribute("error", "Line Manager Staff ID '" + lmStaffId + "' already exists!");
                        return "redirect:/admin/users";
                    }

                    // Create line manager employee
                    Employee lineManager = new Employee();
                    lineManager.setStaffId(lmStaffId);
                    lineManager.setFirstName(lmFirstName);
                    lineManager.setSurName(lmSurName);
                    lineManager.setEmailAddress(lmEmail);
                    lineManager.setPhoneNumber(lmPhone);

                    // Set department for line manager
                    if (lmDepartmentId != null) {
                        Department dept = departmentService.getDepartmentById(lmDepartmentId).orElse(null);
                        lineManager.setDepartment(dept);
                    } else if (departmentId != null) {
                        // Use the same department as the user
                        Department dept = departmentService.getDepartmentById(departmentId).orElse(null);
                        lineManager.setDepartment(dept);
                    }

                    Employee savedLm = employeeService.createEmployee(lineManager);
                    lineManagerEmployeeId = savedLm.getEmployeeId();
                    log.info("✅ New line manager created with ID: {}", lineManagerEmployeeId);

                } else {
                    // Use existing line manager
                    try {
                        lineManagerEmployeeId = Long.parseLong(lineManagerId);
                        log.info("Using existing line manager with ID: {}", lineManagerEmployeeId);
                    } catch (NumberFormatException e) {
                        redirectAttributes.addFlashAttribute("error", "Invalid Line Manager ID format.");
                        return "redirect:/admin/users";
                    }
                }
            }

            // Set the line manager ID on the user DTO
            userDTO.setLineManagerId(lineManagerEmployeeId);

            // Create the user
            AppUser createdUser = userService.createUser(userDTO);

            log.info("✅ User created successfully with ID: {}", createdUser.getUserId());
            log.info("✅ Permissions assigned: {}", createdUser.getPermissions().stream()
                    .map(p -> p.getPermissionName())
                    .collect(java.util.stream.Collectors.toList()));

            redirectAttributes.addFlashAttribute("success",
                    "User '" + username + "' created successfully! A welcome email with temporary password has been sent.");

        } catch (UserAlreadyExistsException e) {
            log.warn("User creation failed - duplicate: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create users.");
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to create user: " + e.getMessage());
        }

        log.info("=== CREATE USER END ===");
        return "redirect:/admin/users";
    }

    @PostMapping("/update")
    @PreAuthorize("hasAnyAuthority('USER_EDIT', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public String updateUser(
            @RequestParam Long userId,
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) Long lineManagerId,
            @RequestParam String role,
            @RequestParam(required = false) boolean active,
            @RequestParam(required = false) boolean blocked,
            @RequestParam(required = false) List<String> permissions,
            RedirectAttributes redirectAttributes) {

        log.info("=== UPDATE USER START ===");
        log.info("Updating user: {}", userId);

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
            return "redirect:/login";
        }

        if (!currentUser.hasAnyPermission("USER_EDIT", "MANAGE_USERS", "ADMIN")) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to update users.");
            return "redirect:/admin/users";
        }

        try {
            UserDTO userDTO = new UserDTO();
            userDTO.setUserId(userId);
            userDTO.setFullName(fullName);
            userDTO.setEmail(email);
            userDTO.setPhoneNumber(phoneNumber);
            userDTO.setDepartmentId(departmentId);
            userDTO.setLineManagerId(lineManagerId);
            userDTO.setRole(role);
            userDTO.setActive(active);
            userDTO.setBlocked(blocked);

            if (permissions != null && !permissions.isEmpty()) {
                userDTO.setPermissions(permissions);
            }

            AppUser updated = userService.updateUser(userId, userDTO);

            log.info("✅ User updated successfully: {}", updated.getUsername());

            redirectAttributes.addFlashAttribute("success",
                    "User '" + updated.getUsername() + "' updated successfully!");

        } catch (UserAlreadyExistsException e) {
            log.warn("User update failed - duplicate: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to update users.");
        } catch (Exception e) {
            log.error("Error updating user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to update user: " + e.getMessage());
        }

        log.info("=== UPDATE USER END ===");
        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/reset-password")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('RESET_PASSWORDS', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> resetPassword(@PathVariable Long userId) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "You must be logged in"));
            }

            log.info("Resetting password for user: {}", userId);
            userService.resetPassword(userId);
            return ResponseEntity.ok().body(Map.of("success", true, "message", "Password reset email sent"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "You don't have permission to reset passwords"));
        } catch (Exception e) {
            log.error("Error resetting password: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{userId}/toggle")
    @PreAuthorize("hasAnyAuthority('USER_EDIT', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public String toggleUser(@PathVariable Long userId, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            userService.toggleUserStatus(userId);
            redirectAttributes.addFlashAttribute("success", "User status toggled successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to toggle user status.");
        } catch (Exception e) {
            log.error("Error toggling user status: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to toggle user status: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/delete")
    @PreAuthorize("hasAnyAuthority('USER_EDIT', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public String deleteUser(@PathVariable Long userId, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            userService.deleteUser(userId);
            redirectAttributes.addFlashAttribute("success", "User deleted successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to delete users.");
        } catch (Exception e) {
            log.error("Error deleting user: {}", e.getMessage(), e);
            String friendlyMessage = "Cannot delete this user because they have associated records in the system. ";
            if (e.getMessage() != null && e.getMessage().contains("TransferTokens")) {
                friendlyMessage += "The user has existing transfer tokens that need to be processed first.";
            } else if (e.getMessage() != null && e.getMessage().contains("REFERENCE constraint")) {
                friendlyMessage += "The user has existing records that reference them. Please remove these references first.";
            } else {
                friendlyMessage += "The user has been deactivated instead.";
            }
            redirectAttributes.addFlashAttribute("error", friendlyMessage);
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/{userId}/edit")
    @PreAuthorize("hasAnyAuthority('USER_EDIT', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public String editUser(@PathVariable Long userId, Model model) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return "redirect:/login";
            }

            AppUser user = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            model.addAttribute("user", user);
            model.addAttribute("allPermissions", permissionService.getAllPermissions());
            model.addAttribute("departments", departmentService.getAllDepartments());
            model.addAttribute("employees", employeeService.getAllEmployees());
            model.addAttribute("roles", List.of("EMPLOYEE", "DRIVER", "INFRA", "FINANCE", "MANAGER", "ADMIN", "SUPERADMIN"));
            model.addAttribute("canEdit", currentUser.hasAnyPermission("USER_EDIT", "MANAGE_USERS", "ADMIN"));
            model.addAttribute("canManageRoles", currentUser.hasAnyPermission("MANAGE_ROLES", "ADMIN"));

            return "admin/user-edit";
        } catch (Exception e) {
            log.error("Error loading edit user page: {}", e.getMessage(), e);
            return "redirect:/admin/users";
        }
    }
}