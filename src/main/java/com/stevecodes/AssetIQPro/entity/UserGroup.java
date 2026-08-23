package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_groups")
@Data
@NoArgsConstructor
public class UserGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "group_name", unique = true, nullable = false, length = 100)
    private String groupName;

    @Column(length = 255)
    private String description;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "is_system_group")
    private boolean systemGroup = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    // EAGER fetch to ensure members are loaded
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_group_members",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<AppUser> members = new HashSet<>();

    // EAGER fetch to ensure permissions are loaded
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_group_permissions",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    public UserGroup(String groupName, String description) {
        this.groupName = groupName;
        this.description = description;
        this.active = true;
    }

    public void addPermission(Permission permission) {
        if (permission != null) {
            permissions.add(permission);
        }
    }

    public void addPermissions(Set<Permission> newPermissions) {
        if (newPermissions != null && !newPermissions.isEmpty()) {
            this.permissions.addAll(newPermissions);
        }
    }

    public void removePermission(Permission permission) {
        permissions.remove(permission);
    }

    public void addMember(AppUser user) {
        if (user != null) {
            members.add(user);
        }
    }

    public void removeMember(AppUser user) {
        members.remove(user);
    }

    public boolean hasPermission(String permissionName) {
        return permissions.stream()
                .anyMatch(p -> p.getPermissionName().equals(permissionName));
    }

    public boolean hasAnyPermission(String... permissionNames) {
        for (String name : permissionNames) {
            if (hasPermission(name)) return true;
        }
        return false;
    }
}