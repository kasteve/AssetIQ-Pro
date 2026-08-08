package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
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

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('CREATE_USERS', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public String createUser(@ModelAttribute UserDTO userDTO, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("Creating user: {}", userDTO.getUsername());
            userService.createUser(userDTO);
            redirectAttributes.addFlashAttribute("success", "User created successfully! Welcome email sent.");
        } catch (UserAlreadyExistsException e) {
            log.warn("User creation failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create users.");
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to create user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/update")
    @PreAuthorize("hasAnyAuthority('USER_EDIT', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public String updateUser(@ModelAttribute UserDTO userDTO, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            log.info("Updating user: {}", userDTO.getUserId());
            log.info("UserDTO received: fullName={}, role={}, departmentId={}, email={}, active={}, blocked={}",
                    userDTO.getFullName(), userDTO.getRole(), userDTO.getDepartmentId(),
                    userDTO.getEmail(), userDTO.isActive(), userDTO.isBlocked());

            if (userDTO.getUserId() == null) {
                redirectAttributes.addFlashAttribute("error", "User ID is required for update.");
                return "redirect:/admin/users";
            }

            userService.updateUser(userDTO.getUserId(), userDTO);
            redirectAttributes.addFlashAttribute("success", "User updated successfully!");

        } catch (UserAlreadyExistsException e) {
            log.warn("User update failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to update users.");
        } catch (Exception e) {
            log.error("Error updating user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to update user: " + e.getMessage());
        }
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