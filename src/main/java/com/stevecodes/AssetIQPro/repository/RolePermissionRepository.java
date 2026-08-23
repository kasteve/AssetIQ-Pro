package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleName(String roleName);

    @Query("SELECT rp.permissionName FROM RolePermission rp WHERE rp.roleName = :roleName")
    Set<String> findPermissionNamesByRoleName(@Param("roleName") String roleName);

    @Query("SELECT rp.roleName FROM RolePermission rp WHERE rp.permissionName = :permissionName")
    List<String> findRolesWithPermission(@Param("permissionName") String permissionName);

    @Modifying
    @Transactional
    @Query("DELETE FROM RolePermission rp WHERE rp.roleName = :roleName")
    void deleteByRoleName(@Param("roleName") String roleName);

    @Modifying
    @Transactional
    @Query("DELETE FROM RolePermission rp WHERE rp.roleName = :roleName AND rp.permissionName = :permissionName")
    void deleteByRoleAndPermission(@Param("roleName") String roleName, @Param("permissionName") String permissionName);
}