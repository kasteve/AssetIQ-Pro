package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sla_configurations")
@Data
@NoArgsConstructor
public class SLAConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Integer configId;

    @Column(name = "config_name", nullable = false, length = 100)
    private String configName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "request_type", nullable = false, length = 50)
    private String requestType; // ASSET_DISPOSAL, TRANSFER, BOOKING, INFRA_REQUEST

    @Column(name = "sla_hours", nullable = false)
    private Integer slaHours;

    @Column(name = "escalation_levels", columnDefinition = "JSON")
    private String escalationLevels;

    @Column(name = "reminder_interval_hours")
    private Integer reminderIntervalHours = 24;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

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
        return isActive ? "Active" : "Inactive";
    }
}