package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.Asset.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Integer> {

    // ============================================
    // Basic queries
    // ============================================
    Optional<Asset> findByTag(String tag);

    Optional<Asset> findBySerialNumber(String serialNumber);

    @Query("SELECT a FROM Asset a WHERE LOWER(TRIM(a.tag)) = LOWER(TRIM(:tag))")
    Optional<Asset> findByTagIgnoreCase(@Param("tag") String tag);

    boolean existsByTag(String tag);

    // ============================================
    // Department-based queries
    // ============================================
    @Query("SELECT a FROM Asset a WHERE a.departmentId = :departmentId")
    List<Asset> findByDepartmentId(@Param("departmentId") Integer departmentId);

    @Query("SELECT a FROM Asset a WHERE a.currentDepartment = :department")
    List<Asset> findByDepartment(@Param("department") String department);

    @Query("SELECT a FROM Asset a WHERE a.departmentId IN (SELECT e.department.departmentId FROM Employee e WHERE e.employeeId = :employeeId)")
    List<Asset> findAssetsByEmployeeDepartment(@Param("employeeId") Long employeeId);

    // ============================================
    // Warranty/EOL queries
    // ============================================
    List<Asset> findByWarrantyExpiryBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT a FROM Asset a WHERE a.warrantyExpiry IS NOT NULL AND a.warrantyExpiry <= :date")
    List<Asset> findAssetsWithWarrantyExpiringOnOrBefore(@Param("date") LocalDate date);

    @Query("SELECT a FROM Asset a WHERE a.eolDate IS NOT NULL AND a.eolDate <= :date")
    List<Asset> findAssetsWithEOLOnOrBefore(@Param("date") LocalDate date);

    @Query("SELECT a FROM Asset a WHERE a.warrantyEndDate IS NOT NULL AND a.warrantyEndDate BETWEEN :today AND :threshold")
    List<Asset> findAssetsWithWarrantyExpiringWithinDays(@Param("today") LocalDate today,
                                                         @Param("threshold") LocalDate threshold);

    @Query("SELECT a FROM Asset a WHERE a.eolDate IS NOT NULL AND a.eolDate BETWEEN :today AND :threshold")
    List<Asset> findAssetsWithEOLWithinDays(@Param("today") LocalDate today,
                                            @Param("threshold") LocalDate threshold);

    @Query("SELECT a FROM Asset a WHERE a.eolDate IS NOT NULL AND a.eolDate <= :date AND a.disposalStatus = 'ACTIVE'")
    List<Asset> findByEolDateBeforeAndDisposalStatus(@Param("date") LocalDate date,
                                                     @Param("disposalStatus") String disposalStatus);

    @Query("SELECT a FROM Asset a WHERE a.retentionPeriodEndDate IS NOT NULL AND a.retentionPeriodEndDate <= :date AND a.disposalStatus = 'ACTIVE'")
    List<Asset> findByRetentionPeriodEndDateBeforeAndDisposalStatus(@Param("date") LocalDate date,
                                                                    @Param("disposalStatus") String disposalStatus);

    // ============================================
    // Status queries
    // ============================================
    List<Asset> findByStatus(AssetStatus status);

    long countByStatus(AssetStatus status);

    @Query("SELECT a.status, COUNT(a) FROM Asset a GROUP BY a.status")
    List<Object[]> countByStatusGrouped();

    // ============================================
    // Category queries
    // ============================================
    @Query("SELECT a.category.name, COUNT(a) FROM Asset a GROUP BY a.category.name")
    List<Object[]> countByCategoryGrouped();

    @Query("SELECT a FROM Asset a WHERE a.category.categoryId = :categoryId")
    List<Asset> findByCategoryId(@Param("categoryId") Integer categoryId);

    // ============================================
    // Search
    // ============================================
    @Query("SELECT a FROM Asset a WHERE LOWER(a.tag) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Asset> searchAssets(@Param("searchTerm") String searchTerm);

    // ============================================
    // All tags for dropdown
    // ============================================
    @Query("SELECT a.tag FROM Asset a WHERE a.tag IS NOT NULL ORDER BY a.tag")
    List<String> findAllAssetTags();

    // ============================================
    // Department distribution
    // ============================================
    @Query(value = "SELECT COALESCE(d.Name, 'Unassigned') as department, COUNT(a.asset_id) as count " +
            "FROM assets a " +
            "LEFT JOIN departments d ON a.department_id = d.department_id " +
            "GROUP BY d.Name", nativeQuery = true)
    List<Object[]> countByDepartmentGroupedNative();

    @Query("SELECT COALESCE(a.currentDepartment, 'Unassigned'), COUNT(a) FROM Asset a GROUP BY a.currentDepartment")
    List<Object[]> countByCurrentDepartmentGrouped();

    // ============================================
    // Recent additions
    // ============================================
    List<Asset> findTop10ByOrderByCreatedAtDesc();

    // ✅ FIXED: Remove the incorrect method - Asset uses Integer, not Long
    // The JpaRepository already provides findById(Integer id) method
}