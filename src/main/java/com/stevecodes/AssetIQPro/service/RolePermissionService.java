package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Permission;
import com.stevecodes.AssetIQPro.entity.RolePermission;
import com.stevecodes.AssetIQPro.repository.PermissionRepository;
import com.stevecodes.AssetIQPro.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    // Default role-to-permission mappings
    private static final Map<String, List<String>> DEFAULT_ROLE_PERMISSIONS = Map.of(
            "EMPLOYEE", List.of(
                    "GENERATE_VOUCHERS",
                    "VIEW_OWN_TRANSACTIONS",
                    "MANAGE_BOOKINGS",
                    "INFRA_REQUEST_VIEW",
                    "INFRA_REQUEST_CREATE",
                    "RESOURCE_REQUEST_VIEW",
                    "RESOURCE_REQUEST_CREATE",
                    "ROOM_VIEW_ALL",
                    "ROOM_BOOK",
                    "ROOM_CANCEL",
                    "DRIVER_VIEW"
            ),
            "MANAGER", List.of(
                    "GENERATE_VOUCHERS",
                    "VIEW_OWN_TRANSACTIONS",
                    "MANAGE_BOOKINGS",
                    "INFRA_REQUEST_VIEW",
                    "INFRA_REQUEST_CREATE",
                    "RESOURCE_REQUEST_VIEW",
                    "RESOURCE_REQUEST_CREATE",
                    "ROOM_VIEW_ALL",
                    "ROOM_BOOK",
                    "ROOM_CANCEL",
                    "DRIVER_VIEW",
                    "APPROVE_LM",
                    "EMPLOYEE_VIEW",
                    "VIEW_REPORTS"
            ),
            "DRIVER", List.of(
                    "DRIVER_VIEW",
                    "DRIVER_REQUEST",
                    "VIEW_OWN_TRANSACTIONS",
                    "GENERATE_VOUCHERS"
            ),
            "INFRA", List.of(
                    "INFRA_REQUEST_VIEW",
                    "INFRA_REQUEST_CREATE",
                    "INFRA_REQUEST_APPROVE",
                    "APPROVE_INFRA",
                    "REVIEW_INFRA",
                    "VIEW_REPORTS",
                    "DOWNLOAD_REPORTS"
            ),
            "FINANCE", List.of(
                    "APPROVE_FINANCE",
                    "VIEW_REPORTS",
                    "DOWNLOAD_REPORTS",
                    "VIEW_ALL_TRANSACTIONS"
            ),
            "ADMIN", List.of(
                    "ADMIN",
                    "MANAGE_ROLES",
                    "CREATE_USERS",
                    "RESET_PASSWORDS",
                    "MANAGE_CONFIG",
                    "VIEW_AUDIT",
                    "VIEW_ALL_TRANSACTIONS",
                    "VIEW_REPORTS",
                    "DOWNLOAD_REPORTS",
                    "USER_VIEW",
                    "USER_EDIT",
                    "USER_DISABLE",
                    "USER_LOCK"
            ),
            "SUPERADMIN", List.of(
                    "SUPER_ADMIN",
                    "ADMIN",
                    "MANAGE_ROLES",
                    "CREATE_USERS",
                    "RESET_PASSWORDS",
                    "MANAGE_CONFIG",
                    "VIEW_AUDIT",
                    "VIEW_ALL_TRANSACTIONS",
                    "VIEW_REPORTS",
                    "DOWNLOAD_REPORTS",
                    "USER_VIEW",
                    "USER_EDIT",
                    "USER_DISABLE",
                    "USER_LOCK",
                    "DELETE_ASSETS",
                    "MANAGE_WARRANTY",
                    "MANAGE_EOL",
                    "MANAGE_INVENTORY"
            )
    );

    /**
     * Get all permissions for a role
     */
    public Set<String> getPermissionsForRole(String roleName) {
        return rolePermissionRepository.findPermissionNamesByRoleName(roleName);
    }

    /**
     * Get all permissions for a user (role + group + direct)
     */
    public Set<String> getAllPermissionsForUser(AppUser user) {
        Set<String> allPermissions = new HashSet<>();

        // 1. Add role-based permissions
        if (user.getRole() != null) {
            allPermissions.addAll(getPermissionsForRole(user.getRole()));
        }

        // 2. Add group-based permissions (via UserGroupService)
        // This will be injected or called separately

        // 3. Add direct permissions
        if (user.getPermissions() != null) {
            allPermissions.addAll(user.getPermissions().stream()
                    .map(Permission::getPermissionName)
                    .collect(Collectors.toSet()));
        }

        return allPermissions;
    }

    /**
     * Get all permissions for a role (default + custom)
     */
    public Set<String> getEffectivePermissionsForRole(String roleName) {
        Set<String> permissions = new HashSet<>();

        // Get from database
        permissions.addAll(rolePermissionRepository.findPermissionNamesByRoleName(roleName));

        // If no permissions found, use defaults
        if (permissions.isEmpty()) {
            permissions.addAll(DEFAULT_ROLE_PERMISSIONS.getOrDefault(roleName, List.of()));
        }

        return permissions;
    }

    /**
     * Sync role permissions (replace all)
     */
    @Transactional
    public void syncRolePermissions(String roleName, List<String> permissionNames, Long updatedBy) {
        log.info("Syncing permissions for role: {}", roleName);

        // Delete existing
        rolePermissionRepository.deleteByRoleName(roleName);

        // Add new
        for (String permName : permissionNames) {
            if (permissionRepository.findByPermissionName(permName).isPresent()) {
                RolePermission rp = new RolePermission(roleName, permName, updatedBy);
                rolePermissionRepository.save(rp);
            } else {
                log.warn("Permission not found: {}", permName);
            }
        }

        auditService.logAction("ROLE_PERMISSIONS_SYNCED",
                "Permissions synced for role: " + roleName + " (" + permissionNames.size() + " permissions)",
                updatedBy);
    }

    /**
     * Add a permission to a role
     */
    @Transactional
    public void addPermissionToRole(String roleName, String permissionName, Long addedBy) {
        log.info("Adding permission {} to role {}", permissionName, roleName);

        if (!permissionRepository.findByPermissionName(permissionName).isPresent()) {
            throw new RuntimeException("Permission not found: " + permissionName);
        }

        // Check if already exists
        Set<String> existing = rolePermissionRepository.findPermissionNamesByRoleName(roleName);
        if (existing.contains(permissionName)) {
            log.info("Permission {} already exists for role {}", permissionName, roleName);
            return;
        }

        RolePermission rp = new RolePermission(roleName, permissionName, addedBy);
        rolePermissionRepository.save(rp);

        auditService.logAction("ROLE_PERMISSION_ADDED",
                "Permission " + permissionName + " added to role: " + roleName,
                addedBy);
    }

    /**
     * Remove a permission from a role
     */
    @Transactional
    public void removePermissionFromRole(String roleName, String permissionName, Long removedBy) {
        log.info("Removing permission {} from role {}", permissionName, roleName);

        rolePermissionRepository.deleteByRoleAndPermission(roleName, permissionName);

        auditService.logAction("ROLE_PERMISSION_REMOVED",
                "Permission " + permissionName + " removed from role: " + roleName,
                removedBy);
    }

    /**
     * Initialize default role permissions
     */
    @Transactional
    public void initializeDefaultRolePermissions() {
        log.info("Initializing default role permissions...");

        for (Map.Entry<String, List<String>> entry : DEFAULT_ROLE_PERMISSIONS.entrySet()) {
            String roleName = entry.getKey();
            List<String> permissions = entry.getValue();

            // Only set if not already configured
            if (rolePermissionRepository.findByRoleName(roleName).isEmpty()) {
                for (String permName : permissions) {
                    if (permissionRepository.findByPermissionName(permName).isPresent()) {
                        RolePermission rp = new RolePermission(roleName, permName);
                        rolePermissionRepository.save(rp);
                    }
                }
                log.info("Initialized {} permissions for role: {}", permissions.size(), roleName);
            }
        }
    }

    /**
     * Get all role-permission mappings
     */
    public Map<String, Set<String>> getAllRolePermissions() {
        List<RolePermission> all = rolePermissionRepository.findAll();
        Map<String, Set<String>> result = new HashMap<>();

        for (RolePermission rp : all) {
            result.computeIfAbsent(rp.getRoleName(), k -> new HashSet<>())
                    .add(rp.getPermissionName());
        }

        return result;
    }

    /**
     * Get roles that have a specific permission
     */
    public List<String> getRolesWithPermission(String permissionName) {
        return rolePermissionRepository.findRolesWithPermission(permissionName);
    }

    /**
     * Check if a role has a specific permission
     */
    public boolean roleHasPermission(String roleName, String permissionName) {
        return rolePermissionRepository.findPermissionNamesByRoleName(roleName)
                .contains(permissionName);
    }
}