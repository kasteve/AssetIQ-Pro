package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    List<StockItem> findByQuantityLessThanEqual(Integer threshold);

    @Query("SELECT s FROM StockItem s WHERE s.quantity <= s.lowStockThreshold AND s.alertSent = false")
    List<StockItem> findItemsBelowThresholdWithoutAlert();

    // ✅ NEW: Find by category
    List<StockItem> findByCategoryId(Long categoryId);

    // ✅ NEW: Find by category and order by name
    List<StockItem> findByCategoryIdOrderByNameAsc(Long categoryId);

    // ✅ NEW: Find all ordered by category then name
    @Query("SELECT s FROM StockItem s ORDER BY s.category.name, s.name")
    List<StockItem> findAllOrderByCategoryAndName();

    // ✅ NEW: Get items with low stock for a specific category
    @Query("SELECT s FROM StockItem s WHERE s.category.id = :categoryId AND s.quantity <= s.lowStockThreshold")
    List<StockItem> findLowStockItemsByCategory(@Param("categoryId") Long categoryId);
}