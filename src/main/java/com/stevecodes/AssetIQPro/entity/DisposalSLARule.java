package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "disposal_sla_rules")
@Data
@NoArgsConstructor
public class DisposalSLARule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sla_rule_id")
    private Integer slaRuleId;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "asset_category_id")
    private Integer assetCategoryId;

    @Column(name = "asset_status", length = 50)
    private String assetStatus;

    @Column(name = "total_sla_hours", nullable = false)
    private Integer totalSlaHours = 168;

    @Column(name = "finance_sla_hours")
    private Integer financeSlaHours = 48;

    @Column(name = "infra_sla_hours")
    private Integer infraSlaHours = 72;

    @Column(name = "compliance_sla_hours")
    private Integer complianceSlaHours = 48;

    @Column(name = "security_sla_hours")
    private Integer securitySlaHours = 24;

    @Column(name = "escalation_reminder_interval")
    private Integer escalationReminderInterval = 24;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_category_id", insertable = false, updatable = false)
    private Category category;

    public Integer getSlaHoursForLevel(String levelName) {
        switch (levelName.toUpperCase()) {
            case "FINANCE":
                return financeSlaHours != null ? financeSlaHours : 48;
            case "INFRASTRUCTURE":
                return infraSlaHours != null ? infraSlaHours : 72;
            case "COMPLIANCE":
                return complianceSlaHours != null ? complianceSlaHours : 48;
            case "SECURITY":
                return securitySlaHours != null ? securitySlaHours : 24;
            default:
                return 48;
        }
    }

    public String getStatusDisplay() {
        return isActive ? "Active" : "Inactive";
    }
}