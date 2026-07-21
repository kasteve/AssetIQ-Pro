package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {

    Optional<SystemSetting> findBySettingKey(String settingKey);

    List<SystemSetting> findByCategory(String category);

    List<SystemSetting> findByActiveTrue();

    @Query("SELECT s FROM SystemSetting s WHERE s.category = :category AND s.active = true")
    List<SystemSetting> findActiveByCategory(@Param("category") String category);

    @Query("SELECT s.settingValue FROM SystemSetting s WHERE s.settingKey = :key AND s.active = true")
    Optional<String> findSettingValueByKey(@Param("key") String key);

    @Modifying
    @Transactional
    @Query("UPDATE SystemSetting s SET s.settingValue = :value, s.updatedAt = CURRENT_TIMESTAMP, s.updatedBy = :updatedBy WHERE s.settingKey = :key")
    int updateSettingValue(@Param("key") String key, @Param("value") String value, @Param("updatedBy") Long updatedBy);

    boolean existsBySettingKey(String settingKey);
}