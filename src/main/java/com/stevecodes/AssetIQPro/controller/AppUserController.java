package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.UserDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.service.AppUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "User Management", description = "Admin user management APIs")
@PreAuthorize("hasAuthority('CREATE_USERS')")
public class AppUserController {

    @Autowired
    private AppUserService userService;

    @GetMapping
    @Operation(summary = "Get all users")
    public ResponseEntity<List<AppUser>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<AppUser> getUserById(@PathVariable Long userId) {
        return userService.getUserById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{userId}/permissions")
    @Operation(summary = "Get user permissions")
    public ResponseEntity<List<String>> getUserPermissions(@PathVariable Long userId) {
        AppUser user = userService.getUserById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<String> permissions = user.getPermissions().stream()
                .map(p -> p.getPermissionName())
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(permissions);
    }

    @PutMapping("/{userId}/permissions")
    @Operation(summary = "Sync user permissions")
    public ResponseEntity<Void> syncPermissions(@PathVariable Long userId,
                                                @RequestBody List<String> permissions) {
        userService.syncPermissions(userId, permissions);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}/role")
    @Operation(summary = "Update user role")
    public ResponseEntity<Void> updateUserRole(@PathVariable Long userId,
                                               @RequestBody Map<String, String> payload) {
        String role = payload.get("role");
        userService.updateUserRole(userId, role);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    @Operation(summary = "Create new user")
    public ResponseEntity<AppUser> createUser(@Valid @RequestBody UserDTO userDTO) {
        AppUser created = userService.createUser(userDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update user")
    public ResponseEntity<AppUser> updateUser(@PathVariable Long userId,
                                              @Valid @RequestBody UserDTO userDTO) {
        AppUser updated = userService.updateUser(userId, userDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/toggle-status")
    @Operation(summary = "Toggle user active status")
    public ResponseEntity<Void> toggleUserStatus(@PathVariable Long userId) {
        userService.toggleUserStatus(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/block")
    @Operation(summary = "Block user")
    public ResponseEntity<Void> blockUser(@PathVariable Long userId) {
        userService.blockUser(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/unblock")
    @Operation(summary = "Unblock user")
    public ResponseEntity<Void> unblockUser(@PathVariable Long userId) {
        userService.unblockUser(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/reset-password")
    @Operation(summary = "Reset user password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable Long userId) {
        AppUser user = userService.getUserById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        String token = userService.generatePasswordResetToken(user.getEmail());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Password reset email sent");
        response.put("resetToken", token);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/permissions/{permissionName}")
    @Operation(summary = "Get users with specific permission")
    public ResponseEntity<List<AppUser>> getUsersWithPermission(@PathVariable String permissionName) {
        return ResponseEntity.ok(userService.getUsersWithPermission(permissionName));
    }
}