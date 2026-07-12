package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Integer> {

    List<Transfer> findByAssetTag(String assetTag);

    List<Transfer> findBySerialNumber(String serialNumber);

    List<Transfer> findByIsFullySignedFalse();

    List<Transfer> findByIsFullySignedTrue();

    List<Transfer> findByTransferDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT t FROM Transfer t WHERE t.oldDepartmentId = :departmentId OR t.newDepartmentId = :departmentId")
    List<Transfer> findTransfersForDepartment(@Param("departmentId") Integer departmentId);

    long countByIsFullySignedTrue();

    long countByIsFullySignedFalse();

    @Query("SELECT t.transferDate, COUNT(t) FROM Transfer t GROUP BY t.transferDate ORDER BY t.transferDate DESC")
    List<Object[]> countTransfersByDate();

    @Query("SELECT t FROM Transfer t WHERE t.transferId <> :excludeId AND (" +
            "  (:assetTag IS NOT NULL AND TRIM(t.assetTag) <> '' " +
            "     AND LOWER(TRIM(t.assetTag)) = LOWER(TRIM(:assetTag))) " +
            "  OR " +
            "  (:serialNumber IS NOT NULL AND TRIM(t.serialNumber) <> '' " +
            "     AND LOWER(TRIM(t.serialNumber)) = LOWER(TRIM(:serialNumber))) " +
            ") ORDER BY t.transferDate ASC, t.transferId ASC")
    List<Transfer> findRelatedTransfers(@Param("assetTag") String assetTag,
                                        @Param("serialNumber") String serialNumber,
                                        @Param("excludeId") Integer excludeId);

    List<Transfer> findTop5ByOrderByTransferDateDesc();
}