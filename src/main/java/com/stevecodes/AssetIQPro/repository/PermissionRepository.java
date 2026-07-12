package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Integer> {

    Optional<Permission> findByPermissionName(String permissionName);

    List<Permission> findAllByOrderByPermissionNameAsc();

    boolean existsByPermissionName(String permissionName);

    @Query("SELECT p.permissionName FROM Permission p ORDER BY p.permissionName")
    List<String> findAllPermissionNames();
}