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
}