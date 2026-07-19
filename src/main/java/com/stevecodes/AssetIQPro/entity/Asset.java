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

    /**
     * Get the number of days until warranty expires
     * @return number of days until expiry, or null if no warranty date
     */
    public Integer getDaysUntilWarrantyExpiry() {
        if (warrantyEndDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if (warrantyEndDate.isBefore(today)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(today, warrantyEndDate);
    }

    /**
     * Get the number of days until EOL (End of Life)
     * @return number of days until EOL, or null if no EOL date
     */
    public Integer getDaysUntilEOL() {
        if (eolDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if (eolDate.isBefore(today)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(today, eolDate);
    }

    /**
     * Check if warranty is expiring soon (within notification days)
     * @return true if warranty is expiring within the notification window
     */
    public boolean isWarrantyExpiringSoon() {
        Integer daysLeft = getDaysUntilWarrantyExpiry();
        if (daysLeft == null) {
            return false;
        }
        int notificationDays = warrantyNotificationDays != null ? warrantyNotificationDays : 30;
        return daysLeft <= notificationDays && daysLeft >= 0;
    }

    /**
     * Check if EOL is soon (within notification days)
     * @return true if EOL is within the notification window
     */
    public boolean isEOLSoon() {
        Integer daysLeft = getDaysUntilEOL();
        if (daysLeft == null) {
            return false;
        }
        int notificationDays = eolNotificationDays != null ? eolNotificationDays : 30;
        return daysLeft <= notificationDays && daysLeft >= 0;
    }

    /**
     * Check if warranty has expired
     * @return true if warranty end date is in the past
     */
    public boolean isWarrantyExpired() {
        if (warrantyEndDate == null) {
            return false;
        }
        return warrantyEndDate.isBefore(LocalDate.now());
    }

    /**
     * Check if EOL has passed
     * @return true if EOL date is in the past
     */
    public boolean isEOLExpired() {
        if (eolDate == null) {
            return false;
        }
        return eolDate.isBefore(LocalDate.now());
    }

    /**
     * Get the asset's status as a display string
     */
    public String getStatusDisplay() {
        if (status == null) {
            return "UNKNOWN";
        }
        return status.name();
    }

    /**
     * Check if the asset is currently available
     */
    public boolean isAvailable() {
        return status == AssetStatus.AVAILABLE;
    }

    /**
     * Check if the asset is currently assigned
     */
    public boolean isAssigned() {
        return status == AssetStatus.ASSIGNED;
    }

    /**
     * Check if the asset is retired
     */
    public boolean isRetired() {
        return status == AssetStatus.RETIRED;
    }

    /**
     * Check if the asset is in maintenance
     */
    public boolean isInMaintenance() {
        return status == AssetStatus.MAINTENANCE;
    }

    public enum AssetStatus {
        AVAILABLE, ASSIGNED, MAINTENANCE, RETIRED, TRANSFERRED, DISPOSED
    }
}