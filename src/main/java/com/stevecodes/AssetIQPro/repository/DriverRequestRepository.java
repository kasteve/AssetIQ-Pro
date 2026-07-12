package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.DriverRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DriverRequestRepository extends JpaRepository<DriverRequest, Long> {
    List<DriverRequest> findAllByOrderByRequestTimeDesc();
    List<DriverRequest> findByDriverIdAndStatus(Long driverId, String status);
    List<DriverRequest> findByUserId(Long userId);
    List<DriverRequest> findByDriverId(Long driverId);
}