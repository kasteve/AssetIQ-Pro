package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.AssetHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AssetHistoryRepository extends JpaRepository<AssetHistory, Long> {

    List<AssetHistory> findByAssetIdOrderByEventDateDesc(Integer assetId);

    List<AssetHistory> findByAssetIdAndEventTypeOrderByEventDateDesc(Integer assetId, String eventType);

    @Query("SELECT ah FROM AssetHistory ah WHERE ah.assetId = :assetId AND ah.eventDate BETWEEN :startDate AND :endDate")
    List<AssetHistory> findByAssetIdAndDateRange(@Param("assetId") Integer assetId,
                                                 @Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    @Query("SELECT ah.eventType, COUNT(ah) FROM AssetHistory ah GROUP BY ah.eventType")
    List<Object[]> countByEventTypeGrouped();

    // FIXED: Use @Query instead of method name
    @Query("SELECT ah FROM AssetHistory ah WHERE ah.eventType = :eventType AND ah.eventDate BETWEEN :startDate AND :endDate")
    List<AssetHistory> findByEventTypeAndDateRange(@Param("eventType") String eventType,
                                                   @Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);
}