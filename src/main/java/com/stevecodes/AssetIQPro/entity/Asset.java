package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "Assets", schema = "dbo")
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"category", "supplier", "location", "department"})
@ToString(exclude = {"category", "supplier", "location", "department"})
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AssetId")
    private Integer assetId;

    @Column(name = "Tag", unique = true, nullable = false, length = 50)
    private String tag;

    @Column(name = "Name", length = 100)
    private String name;

    @Column(name = "SerialNumber", length = 50)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CategoryId")
    private Category category;

    @Column(name = "PurchaseDate")
    private LocalDate purchaseDate;

    @Column(name = "PurchaseCost", precision = 18, scale = 2)
    private BigDecimal purchaseCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SupplierId")
    private Supplier supplier;

    @Column(name = "WarrantyExpiry")
    private LocalDate warrantyExpiry;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status")
    private AssetStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LocationId")
    private Location location;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "warranty_years")
    private Integer warrantyYears;

    @Column(name = "eol_date")
    private LocalDate eolDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Column(name = "purchase_invoice_path")
    private String purchaseInvoicePath;

    @Column(name = "eol_notification_days")
    private Integer eolNotificationDays = 30;

    @Column(name = "warranty_notification_days")
    private Integer warrantyNotificationDays = 30;

    @Column(name = "current_department")
    private String currentDepartment;

    @Column(name = "department_id")
    private Integer departmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", insertable = false, updatable = false)
    private Department department;

    // ============================================
    // FIXED: Helper Methods
    // ============================================

    public Integer getDaysUntilWarrantyExpiry() {
        if (warrantyEndDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        // Returns negative days if already expired
        return (int) ChronoUnit.DAYS.between(today, warrantyEndDate);
    }

    public Integer getDaysUntilEOL() {
        if (eolDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        // Returns negative days if already expired
        return (int) ChronoUnit.DAYS.between(today, eolDate);
    }

    /**
     * Check if warranty is expiring soon (future date within notification period)
     */
    public boolean isWarrantyExpiringSoon() {
        Integer daysLeft = getDaysUntilWarrantyExpiry();
        if (daysLeft == null) {
            return false;
        }
        int notificationDays = warrantyNotificationDays != null ? warrantyNotificationDays : 30;
        // Only show "soon" if the date is in the future AND within notification period
        return daysLeft > 0 && daysLeft <= notificationDays;
    }

    /**
     * Check if EOL is approaching soon (future date within notification period)
     */
    public boolean isEOLSoon() {
        Integer daysLeft = getDaysUntilEOL();
        if (daysLeft == null) {
            return false;
        }
        int notificationDays = eolNotificationDays != null ? eolNotificationDays : 30;
        // Only show "soon" if the date is in the future AND within notification period
        return daysLeft > 0 && daysLeft <= notificationDays;
    }

    /**
     * Check if warranty has expired
     */
    public boolean isWarrantyExpired() {
        if (warrantyEndDate == null) {
            return false;
        }
        return warrantyEndDate.isBefore(LocalDate.now());
    }

    /**
     * Check if EOL has passed
     */
    public boolean isEOLExpired() {
        if (eolDate == null) {
            return false;
        }
        return eolDate.isBefore(LocalDate.now());
    }

    public String getStatusDisplay() {
        if (status == null) {
            return "UNKNOWN";
        }
        return status.name();
    }

    public boolean isAvailable() {
        return status == AssetStatus.AVAILABLE;
    }

    public boolean isAssigned() {
        return status == AssetStatus.ASSIGNED;
    }

    public boolean isRetired() {
        return status == AssetStatus.RETIRED;
    }

    public boolean isInMaintenance() {
        return status == AssetStatus.MAINTENANCE;
    }

    public String getAssetName() {
        return name != null ? name : tag;
    }

    public LocalDate getWarrantyStartDate() {
        return purchaseDate;
    }

    public double getCost() {
        return purchaseCost != null ? purchaseCost.doubleValue() : 0.0;
    }

    public enum AssetStatus {
        AVAILABLE, ASSIGNED, MAINTENANCE, RETIRED, TRANSFERRED, DISPOSED
    }
}