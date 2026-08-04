package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_retention_policies")
@Data
@NoArgsConstructor
public class AssetRetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Integer policyId;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "retention_period_years", nullable = false)
    private Integer retentionPeriodYears;

    @Column(name = "disposal_method", nullable = false, length = 50)
    private String disposalMethod;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "description", length = 500)
    private String description;

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

    // ============================================
    // Helper Methods
    // ============================================

    public String getStatusDisplay() {
        return isActive ? "Active" : "Inactive";
    }

    public String getDisposalMethodDisplay() {
        if (disposalMethod == null) return "";
        switch (disposalMethod.toUpperCase()) {
            case "PHYSICAL_DESTRUCTION": return "Physical Destruction";
            case "DEGAUSSING": return "Degaussing";
            case "OVERWRITE": return "Overwrite";
            case "SHREDDED": return "Shredded";
            case "RECYCLED": return "Recycled";
            case "INCINERATION": return "Incineration";
            default: return disposalMethod;
        }
    }
}