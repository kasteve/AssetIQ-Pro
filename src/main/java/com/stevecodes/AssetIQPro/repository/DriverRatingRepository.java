package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.DriverRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRatingRepository extends JpaRepository<DriverRating, Long> {

    Optional<DriverRating> findByRequestId(Long requestId);

    @Query("SELECT dr FROM DriverRating dr WHERE dr.token = :token")
    Optional<DriverRating> findByToken(@Param("token") String token);

    List<DriverRating> findByDriverId(Long driverId);

    List<DriverRating> findByUserId(Long userId);

    @Query("SELECT AVG(dr.rating) FROM DriverRating dr WHERE dr.driverId = :driverId")
    Double getAverageRatingForDriver(@Param("driverId") Long driverId);

    @Query("SELECT COUNT(dr) FROM DriverRating dr WHERE dr.driverId = :driverId")
    Long getRatingCountForDriver(@Param("driverId") Long driverId);
}