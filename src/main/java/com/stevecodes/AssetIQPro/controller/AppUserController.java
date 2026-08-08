package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.UserDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.exception.UserAlreadyExistsException;
import com.stevecodes.AssetIQPro.service.AppUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "User Management", description = "Admin user management APIs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('CREATE_USERS', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
public class AppUserController {

    private final AppUserService userService;

    @GetMapping
    @Operation(summary = "Get all users")
    @PreAuthorize("hasAnyAuthority('USER_VIEW', 'CREATE_USERS', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<AppUser>> getAllUsers() {
        try {
            return ResponseEntity.ok(userService.getAllUsers());
        } catch (Exception e) {
            log.error("Error getting all users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    @PreAuthorize("hasAnyAuthority('USER_VIEW', 'CREATE_USERS', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AppUser> getUserById(@PathVariable Long userId) {
        try {
            return userService.getUserById(userId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error getting user by ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{userId}/permissions")
    @Operation(summary = "Get user permissions")
    @PreAuthorize("hasAnyAuthority('MANAGE_ROLES', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getUserPermissions(@PathVariable Long userId) {
        try {
            AppUser user = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            List<String> permissions = user.getPermissions().stream()
                    .map(p -> p.getPermissionName())
                    .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(permissions);
        } catch (RuntimeException e) {
            log.warn("User not found: {}", userId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting permissions for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get user permissions"));
        }
    }

    @PutMapping("/{userId}/permissions")
    @Operation(summary = "Sync user permissions")
    @PreAuthorize("hasAnyAuthority('MANAGE_ROLES', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> syncPermissions(@PathVariable Long userId,
                                             @RequestBody List<String> permissions) {
        try {
            userService.syncPermissions(userId, permissions);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error syncing permissions for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to sync permissions: " + e.getMessage()));
        }
    }

    @PutMapping("/{userId}/role")
    @Operation(summary = "Update user role")
    @PreAuthorize("hasAnyAuthority('MANAGE_ROLES', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> updateUserRole(@PathVariable Long userId,
                                            @RequestBody Map<String, String> payload) {
        try {
            String role = payload.get("role");
            if (role == null || role.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Role is required"));
            }
            userService.updateUserRole(userId, role);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error updating role for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update user role: " + e.getMessage()));
        }
    }

    @PostMapping
    @Operation(summary = "Create new user")
    @PreAuthorize("hasAnyAuthority('CREATE_USERS', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> createUser(@Valid @RequestBody UserDTO userDTO) {
        try {
            AppUser created = userService.createUser(userDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (UserAlreadyExistsException e) {
            log.warn("User creation failed - duplicate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create user: " + e.getMessage()));
        }
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update user")
    @PreAuthorize("hasAnyAuthority('USER_EDIT', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable Long userId,
                                        @Valid @RequestBody UserDTO userDTO) {
        try {
            userDTO.setUserId(userId);
            AppUser updated = userService.updateUser(userId, userDTO);
            return ResponseEntity.ok(updated);
        } catch (UserAlreadyExistsException e) {
            log.warn("User update failed - duplicate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (AccessDeniedException e) {
            log.warn("Access denied updating user: {}", userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to update this user"));
        } catch (Exception e) {
            log.error("Error updating user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update user: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user")
    @PreAuthorize("hasAnyAuthority('USER_EDIT', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        try {
            userService.deleteUser(userId);
            return ResponseEntity.noContent().build();
        } catch (AccessDeniedException e) {
            log.warn("Access denied deleting user: {}", userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to delete this user"));
        } catch (Exception e) {
            log.error("Error deleting user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete user: " + e.getMessage()));
        }
    }

    @PostMapping("/{userId}/toggle-status")
    @Operation(summary = "Toggle user active status")
    @PreAuthorize("hasAnyAuthority('USER_EDIT', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long userId) {
        try {
            userService.toggleUserStatus(userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "User status toggled successfully"));
        } catch (Exception e) {
            log.error("Error toggling user status {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to toggle user status: " + e.getMessage()));
        }
    }

    @PostMapping("/{userId}/block")
    @Operation(summary = "Block user")
    @PreAuthorize("hasAnyAuthority('USER_EDIT', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> blockUser(@PathVariable Long userId) {
        try {
            userService.blockUser(userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "User blocked successfully"));
        } catch (Exception e) {
            log.error("Error blocking user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to block user: " + e.getMessage()));
        }
    }

    @PostMapping("/{userId}/unblock")
    @Operation(summary = "Unblock user")
    @PreAuthorize("hasAnyAuthority('USER_EDIT', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> unblockUser(@PathVariable Long userId) {
        try {
            userService.unblockUser(userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "User unblocked successfully"));
        } catch (Exception e) {
            log.error("Error unblocking user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to unblock user: " + e.getMessage()));
        }
    }

    @PostMapping("/{userId}/reset-password")
    @Operation(summary = "Reset user password")
    @PreAuthorize("hasAnyAuthority('RESET_PASSWORDS', 'MANAGE_USERS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> resetPassword(@PathVariable Long userId) {
        try {
            AppUser user = userService.getUserById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            userService.resetPassword(userId);
            Map<String, String> response = new HashMap<>();
            response.put("success", "true");
            response.put("message", "Password reset email sent successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error resetting password for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reset password: " + e.getMessage()));
        }
    }

    @GetMapping("/permissions/{permissionName}")
    @Operation(summary = "Get users with specific permission")
    @PreAuthorize("hasAnyAuthority('MANAGE_ROLES', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getUsersWithPermission(@PathVariable String permissionName) {
        try {
            List<AppUser> users = userService.getUsersWithPermission(permissionName);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("Error getting users with permission {}: {}", permissionName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get users with permission: " + e.getMessage()));
        }
    }
}