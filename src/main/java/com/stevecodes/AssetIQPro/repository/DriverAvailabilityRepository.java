package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.DriverAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverAvailabilityRepository extends JpaRepository<DriverAvailability, Long> {

    Optional<DriverAvailability> findByDriverId(Long driverId);
}