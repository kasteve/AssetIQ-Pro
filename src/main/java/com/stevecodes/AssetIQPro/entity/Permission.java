package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "permissions")
@Data
@NoArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Integer permissionId;

    @Column(name = "permission_name", unique = true, nullable = false, length = 100)
    private String permissionName;

    @Column(length = 255)
    private String description;

    // ============================================
    // Group Management Permission Constants
    // ============================================
    public static final String MANAGE_GROUPS = "MANAGE_GROUPS";
    public static final String VIEW_GROUPS = "VIEW_GROUPS";

    public Permission(String permissionName, String description) {
        this.permissionName = permissionName;
        this.description = description;
    }

    @Override
    public String toString() {
        return permissionName;
    }
}