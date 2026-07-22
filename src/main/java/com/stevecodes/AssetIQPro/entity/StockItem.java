package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_items")
@Data
@NoArgsConstructor
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
    private Integer lowStockThreshold = 5;   // default 5

    private String unit;                     // e.g., "pcs", "boxes", "liters"

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    // This can optionally link to a Category
    // private Long categoryId;

    // Whether low-stock alert was already sent (to avoid spamming)
    @Column(name = "alert_sent")
    private boolean alertSent = false;
}