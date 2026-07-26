package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.DriverRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DriverRequestRepository extends JpaRepository<DriverRequest, Long> {

    List<DriverRequest> findByUserId(Long userId);

    List<DriverRequest> findByDriverId(Long driverId);

    List<DriverRequest> findByDriverIdAndStatus(Long driverId, String status);

    List<DriverRequest> findByStatus(String status);

    List<DriverRequest> findAllByOrderByRequestTimeDesc();

    @Query("SELECT d FROM DriverRequest d WHERE d.driverId = :driverId AND d.status IN :statuses")
    List<DriverRequest> findByDriverIdAndStatusIn(@Param("driverId") Long driverId,
                                                  @Param("statuses") List<String> statuses);

    @Query("SELECT d FROM DriverRequest d WHERE d.driverId = :driverId " +
            "AND d.requestTime BETWEEN :startDate AND :endDate " +
            "AND d.status IN :statuses")
    List<DriverRequest> findByDriverIdAndRequestTimeBetweenAndStatusIn(
            @Param("driverId") Long driverId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("statuses") List<String> statuses);

    @Query("SELECT COUNT(d) FROM DriverRequest d WHERE d.driverId = :driverId " +
            "AND d.requestTime BETWEEN :startDate AND :endDate " +
            "AND d.status IN :statuses")
    long countByDriverIdAndRequestTimeBetweenAndStatusIn(
            @Param("driverId") Long driverId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("statuses") List<String> statuses);

    List<DriverRequest> findByDriverIdAndStatusNot(Long driverId, String status);

    @Query("SELECT d FROM DriverRequest d WHERE d.driverId IS NULL AND d.status = 'PENDING'")
    List<DriverRequest> findUnassignedPendingRequests();

    @Query("SELECT d FROM DriverRequest d WHERE d.driverId = :driverId AND d.status IN :statuses ORDER BY d.requestTime DESC")
    List<DriverRequest> findRecentDriverRequests(@Param("driverId") Long driverId,
                                                 @Param("statuses") List<String> statuses);

    @Query("SELECT d FROM DriverRequest d WHERE d.driverId = :driverId AND d.status = 'COMPLETED' ORDER BY d.responseTime DESC")
    List<DriverRequest> findCompletedTripsByDriverId(@Param("driverId") Long driverId);

    @Query("SELECT d FROM DriverRequest d WHERE d.userId = :userId AND d.status = 'COMPLETED' ORDER BY d.responseTime DESC")
    List<DriverRequest> findCompletedTripsByUserId(@Param("userId") Long userId);

    @Query("SELECT d FROM DriverRequest d WHERE d.driverId IS NULL AND d.status = 'PENDING_ADMIN'")
    List<DriverRequest> findPendingCabRequests();

    @Query("SELECT d FROM DriverRequest d WHERE d.status = 'PENDING' AND d.driverId IS NOT NULL")
    List<DriverRequest> findPendingRequestsWithDriver();

    @Query("SELECT d FROM DriverRequest d WHERE d.driverId = :driverId AND d.status = 'PENDING'")
    List<DriverRequest> findPendingRequestsForDriver(@Param("driverId") Long driverId);

    @Query("SELECT AVG(d.rating) FROM DriverRequest d WHERE d.driverId = :driverId AND d.rating IS NOT NULL")
    Double findAverageRatingForDriver(@Param("driverId") Long driverId);

    List<DriverRequest> findTop5ByOrderByRequestTimeDesc();

    @Query("SELECT d FROM DriverRequest d WHERE d.userId = :userId ORDER BY d.requestTime DESC")
    List<DriverRequest> findTop5ByUserId(@Param("userId") Long userId);
}