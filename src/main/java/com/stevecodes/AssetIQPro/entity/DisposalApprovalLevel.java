package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "disposal_approval_levels")
@Data
@NoArgsConstructor
public class DisposalApprovalLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "level_id")
    private Integer levelId;

    @Column(name = "level_order", nullable = false)
    private Integer levelOrder;

    @Column(name = "level_name", nullable = false, length = 100)
    private String levelName; // FINANCE, INFRASTRUCTURE, COMPLIANCE, SECURITY

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName; // Role required to approve

    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName; // Permission required

    @Column(name = "sla_hours")
    private Integer slaHours = 48;

    @Column(name = "is_mandatory")
    private Boolean isMandatory = true;

    @Column(name = "escalation_after_hours")
    private Integer escalationAfterHours = 24;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private AppUser creator;

    public String getStatusDisplay() {
        return levelName;
    }

    public String getColorClass() {
        switch (levelName.toUpperCase()) {
            case "FINANCE":
                return "bg-info";
            case "INFRASTRUCTURE":
                return "bg-warning";
            case "COMPLIANCE":
                return "bg-success";
            case "SECURITY":
                return "bg-danger";
            default:
                return "bg-secondary";
        }
    }
}