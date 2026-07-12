package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.entity.InfraRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface InfraRequestRepository extends JpaRepository<InfraRequest, Long> {

    List<InfraRequest> findByRequesterId(Long requesterId);

    List<InfraRequest> findByLineManagerId(Long lineManagerId);

    List<InfraRequest> findByStatus(RequestStatus status);

    @Query("SELECT ir FROM InfraRequest ir WHERE ir.requesterId = :userId OR ir.lineManagerId = :userId")
    List<InfraRequest> findRequestsForUser(@Param("userId") Long userId);

    @Query("SELECT ir FROM InfraRequest ir WHERE ir.status = :status AND ir.createdAt BETWEEN :startDate AND :endDate")
    List<InfraRequest> findByStatusAndDateRange(@Param("status") RequestStatus status,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);

    @Query("SELECT ir.status, COUNT(ir) FROM InfraRequest ir GROUP BY ir.status")
    List<Object[]> countByStatusGrouped();

    @Query("SELECT ir.resourceType, COUNT(ir) FROM InfraRequest ir GROUP BY ir.resourceType ORDER BY COUNT(ir) DESC")
    List<Object[]> countByResourceTypeGrouped();

    List<InfraRequest> findTop10ByOrderByCreatedAtDesc();

    Collection<Object> findByCreatedAtBetween(LocalDateTime monthStart, LocalDateTime monthEnd);

    List<InfraRequest> findTop5ByOrderByCreatedAtDesc();
}