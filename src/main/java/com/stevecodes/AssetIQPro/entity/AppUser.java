package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // ============================================
    // Lockout tracking (Password Policy: Max Login Attempts)
    // ============================================
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

    // Department relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department departmentEntity;

    // Employee relationship (one-to-one)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_permissions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private List<Permission> permissions = new ArrayList<>();

    public void addPermission(Permission permission) {
        if (!permissions.contains(permission)) {
            permissions.add(permission);
        }
    }

    public boolean hasPermission(String permissionName) {
        return permissions.stream()
                .anyMatch(p -> p.getPermissionName().equals(permissionName));
    }

    public boolean hasRole(String roleName) {
        return role != null && role.equals(roleName);
    }

    // Admin check - uses MANAGE_ROLES permission
    public boolean isAdmin() {
        return hasPermission("MANAGE_ROLES") ||
                hasPermission("ADMIN") ||
                hasPermission("SUPER_ADMIN") ||
                hasRole("ADMIN");
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

    public boolean isInfrastructure() {
        return hasRole("INFRA") || hasRole("INFRASTRUCTURE") || hasPermission("APPROVE_INFRA");
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