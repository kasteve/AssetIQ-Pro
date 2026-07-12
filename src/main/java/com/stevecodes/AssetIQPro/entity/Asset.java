package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Assets")
@Data
@NoArgsConstructor
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AssetId")
    private Integer assetId;

    @Column(nullable = false, unique = true)
    private String tag;

    @Column(nullable = false)
    private String name;

    @Column(name = "SerialNumber")
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CategoryId")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SupplierId")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LocationId")
    private Location location;

    @Column(name = "PurchaseDate")
    private LocalDate purchaseDate;

    @Column(name = "PurchaseCost", precision = 12, scale = 2)
    private BigDecimal purchaseCost;

    @Column(name = "WarrantyExpiry")
    private LocalDate warrantyExpiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetStatus status = AssetStatus.AVAILABLE;

    @Column(name = "warranty_years")
    private Integer warrantyYears = 1;

    @Column(name = "eol_date")
    private LocalDate eolDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Column(name = "purchase_invoice_path", length = 500)
    private String purchaseInvoicePath;

    @Column(name = "eol_notification_days")
    private Integer eolNotificationDays = 30;

    @Column(name = "warranty_notification_days")
    private Integer warrantyNotificationDays = 30;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssetAssignment> assignments = new ArrayList<>();

    public enum AssetStatus {
        AVAILABLE, ASSIGNED, MAINTENANCE, RETIRED, TRANSFERRED
    }

    public int getDaysUntilWarrantyExpiry() {
        if (warrantyEndDate == null) return -1;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), warrantyEndDate);
    }

    public int getDaysUntilEOL() {
        if (eolDate == null) return -1;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), eolDate);
    }

    public boolean isWarrantyExpiringSoon() {
        int days = getDaysUntilWarrantyExpiry();
        return days >= 0 && days <= warrantyNotificationDays;
    }

    public boolean isEOLSoon() {
        int days = getDaysUntilEOL();
        return days >= 0 && days <= eolNotificationDays;
    }

    public boolean getWarrantyExpiringSoon() {
        return isWarrantyExpiringSoon();
    }

    public boolean getEolSoon() {
        return isEOLSoon();
    }
}