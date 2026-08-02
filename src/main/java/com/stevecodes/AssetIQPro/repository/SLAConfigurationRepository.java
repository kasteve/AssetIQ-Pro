package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.SLAConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SLAConfigurationRepository extends JpaRepository<SLAConfiguration, Integer> {

    List<SLAConfiguration> findByRequestTypeAndIsActiveTrue(String requestType);

    Optional<SLAConfiguration> findByRequestTypeAndIsDefaultTrue(String requestType);

    Optional<SLAConfiguration> findFirstByRequestTypeAndIsActiveTrue(String requestType);

    List<SLAConfiguration> findByIsActiveTrue();

    List<SLAConfiguration> findByRequestType(String requestType);

    @Query("SELECT sc FROM SLAConfiguration sc WHERE sc.isActive = true ORDER BY sc.requestType, sc.configName")
    List<SLAConfiguration> findAllActiveOrdered();

    @Query("SELECT sc FROM SLAConfiguration sc WHERE sc.requestType = :requestType AND sc.isActive = true")
    List<SLAConfiguration> findActiveByRequestType(@Param("requestType") String requestType);

    @Query("SELECT sc FROM SLAConfiguration sc WHERE sc.isDefault = true AND sc.isActive = true")
    List<SLAConfiguration> findAllDefaultConfigs();

    boolean existsByRequestTypeAndIsDefaultTrue(String requestType);
}