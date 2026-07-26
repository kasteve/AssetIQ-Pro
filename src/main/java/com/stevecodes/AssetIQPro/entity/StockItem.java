package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold = 5;

    private String unit;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "alert_sent")
    private boolean alertSent = false;

    // ✅ NEW: Category relationship
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private StockCategory category;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    // ✅ Helper method to get category name
    public String getCategoryName() {
        return category != null ? category.getName() : "Uncategorized";
    }
}