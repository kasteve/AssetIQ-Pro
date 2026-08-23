package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_users")
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"permissions", "employee", "departmentEntity"})
@ToString(exclude = {"permissions", "employee", "departmentEntity"})
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "staff_id", unique = true, nullable = false, length = 50)
    private String staffId;

    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(length = 255)
    private String department;

    @Column(name = "role", length = 50)
    private String role = "EMPLOYEE";

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "is_blocked")
    private boolean blocked = false;

    @Column(name = "must_change_password")
    private boolean mustChangePassword = true;

    @Column(name = "is_first_login")
    private boolean firstLogin = true;

    @Column(name = "last_password_changed")
    private LocalDateTime lastPasswordChanged;

    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_expiry")
    private LocalDateTime passwordResetExpiry;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department departmentEntity;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "members")
    private Set<UserGroup> groups = new HashSet<>();

    // ✅ FIXED: Use Set instead of List to prevent duplicates
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_permissions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    // ============================================
    // ✅ FIXED: Permission Helper Methods
    // ============================================

    public void addPermission(Permission permission) {
        if (permission != null) {
            permissions.add(permission);
        }
    }

    /**
     * ✅ FIXED: Add multiple permissions at once with deduplication
     */
    public void addPermissions(Set<Permission> newPermissions) {
        if (newPermissions != null && !newPermissions.isEmpty()) {
            permissions.addAll(newPermissions);
        }
    }

    /**
     * Check if user has a specific permission.
     * Does NOT call isAdmin() to avoid infinite recursion.
     */
    public boolean hasPermission(String permissionName) {
        if (permissionName == null) return false;
        return permissions.stream()
                .anyMatch(p -> p.getPermissionName().equals(permissionName));
    }

    public boolean hasAnyPermission(String... permissionNames) {
        if (permissionNames == null) return false;
        for (String name : permissionNames) {
            if (hasPermission(name)) return true;
        }
        return false;
    }

    public boolean hasAllPermissions(String... permissionNames) {
        if (permissionNames == null) return false;
        for (String name : permissionNames) {
            if (!hasPermission(name)) return false;
        }
        return true;
    }

    public boolean hasRole(String roleName) {
        return role != null && role.equals(roleName);
    }

    public boolean hasAnyRole(String... roleNames) {
        if (roleNames == null) return false;
        for (String name : roleNames) {
            if (hasRole(name)) return true;
        }
        return false;
    }

    /**
     * Check if user is an admin.
     * Does NOT call hasPermission() to avoid infinite recursion.
     * Checks permissions and role directly.
     */
    public boolean isAdmin() {
        return permissions.stream()
                .anyMatch(p -> p.getPermissionName().equals("MANAGE_ROLES") ||
                        p.getPermissionName().equals("ADMIN") ||
                        p.getPermissionName().equals("SUPER_ADMIN")) ||
                hasRole("ADMIN") ||
                hasRole("SUPERADMIN");
    }

    public boolean canManageUsers() {
        return hasPermission("CREATE_USERS") || hasPermission("MANAGE_USERS") || isAdmin();
    }

    public boolean canManageRoles() {
        return hasPermission("MANAGE_ROLES") || isAdmin();
    }

    public boolean canViewAudit() {
        return hasPermission("VIEW_AUDIT") || isAdmin();
    }

    public boolean canManageBookings() {
        return hasPermission("MANAGE_BOOKINGS") || isAdmin();
    }

    public boolean canManageDriverRequests() {
        return hasPermission("MANAGE_DRIVER_REQUESTS") || isAdmin();
    }

    public boolean canReviewInfra() {
        return hasPermission("REVIEW_INFRA") || hasPermission("APPROVE_INFRA") || isAdmin();
    }

    public boolean canManageResources() {
        return hasPermission("MANAGE_RESOURCE_REQUESTS") || isAdmin();
    }

    public boolean canManageConfig() {
        return hasPermission("MANAGE_CONFIG") || isAdmin();
    }

    public boolean canManageAssets() {
        return hasPermission("ASSET_VIEW") || hasPermission("ASSET_CREATE") ||
                hasPermission("ASSET_EDIT") || hasPermission("DELETE_ASSETS") || isAdmin();
    }

    public boolean canViewTransfers() {
        return hasPermission("TRANSFER_VIEW") || hasPermission("TRANSFER_CREATE") ||
                hasPermission("VIEW_ALL_TRANSACTIONS") || isAdmin();
    }

    public boolean canCreateTransfers() {
        return hasPermission("TRANSFER_CREATE") || isAdmin();
    }

    public boolean isInfrastructure() {
        return hasRole("INFRA") || hasRole("INFRASTRUCTURE") ||
                hasPermission("APPROVE_INFRA") || hasPermission("REVIEW_INFRA");
    }

    public boolean isFinance() {
        return hasRole("FINANCE") || hasPermission("APPROVE_FINANCE");
    }

    public boolean isDriver() {
        return hasRole("DRIVER") || hasPermission("DRIVER_VIEW");
    }

    public String getFullName() {
        if (fullName != null && !fullName.isEmpty()) {
            return fullName;
        }
        if (employee != null) {
            return employee.getFullName();
        }
        return username;
    }

    public String getEmail() {
        return email;
    }
}