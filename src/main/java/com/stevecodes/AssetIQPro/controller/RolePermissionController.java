package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.PermissionService;
import com.stevecodes.AssetIQPro.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/role-permissions")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;
    private final PermissionService permissionService;

    private static final List<String> AVAILABLE_ROLES = List.of(
            "EMPLOYEE", "MANAGER", "DRIVER", "INFRA", "FINANCE", "ADMIN", "SUPERADMIN"
    );

    @GetMapping
    public String rolePermissions(Model model) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (!currentUser.hasAnyPermission("MANAGE_ROLES", "ADMIN")) {
            throw new AccessDeniedException("You don't have permission to manage role permissions.");
        }

        Map<String, Set<String>> allRolePermissions = rolePermissionService.getAllRolePermissions();
        model.addAttribute("roles", AVAILABLE_ROLES);
        model.addAttribute("allPermissions", permissionService.getAllPermissions());
        model.addAttribute("rolePermissions", allRolePermissions);
        model.addAttribute("canManage", currentUser.hasAnyPermission("MANAGE_ROLES", "ADMIN"));

        return "admin/role-permissions";
    }

    @PostMapping("/sync")
    public String syncRolePermissions(@RequestParam String roleName,
                                      @RequestParam(required = false) List<String> permissions) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            List<String> perms = permissions != null ? permissions : List.of();
            rolePermissionService.syncRolePermissions(roleName, perms, currentUser.getUserId());
            return "redirect:/admin/role-permissions?success=Permissions updated for " + roleName;
        } catch (Exception e) {
            log.error("Error syncing role permissions: {}", e.getMessage());
            return "redirect:/admin/role-permissions?error=" + e.getMessage();
        }
    }

    @PostMapping("/initialize-defaults")
    public String initializeDefaults() {
        try {
            rolePermissionService.initializeDefaultRolePermissions();
            return "redirect:/admin/role-permissions?success=Default permissions initialized";
        } catch (Exception e) {
            return "redirect:/admin/role-permissions?error=" + e.getMessage();
        }
    }
}