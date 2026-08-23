package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "role_permissions")
@Data
@NoArgsConstructor
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_permission_id")
    private Long rolePermissionId;

    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    public RolePermission(String roleName, String permissionName) {
        this.roleName = roleName;
        this.permissionName = permissionName;
    }

    public RolePermission(String roleName, String permissionName, Long createdBy) {
        this.roleName = roleName;
        this.permissionName = permissionName;
        this.createdBy = createdBy;
    }
}