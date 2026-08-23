package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserGroupRepository extends JpaRepository<UserGroup, Long> {

    Optional<UserGroup> findByGroupName(String groupName);

    List<UserGroup> findByActiveTrue();

    @Query("SELECT g FROM UserGroup g LEFT JOIN FETCH g.members LEFT JOIN FETCH g.permissions WHERE g.groupId = :groupId")
    Optional<UserGroup> findByIdWithMembersAndPermissions(@Param("groupId") Long groupId);

    @Query("SELECT g FROM UserGroup g JOIN g.members m WHERE m.userId = :userId")
    List<UserGroup> findGroupsByMemberId(@Param("userId") Long userId);

    @Query("SELECT g FROM UserGroup g JOIN g.permissions p WHERE p.permissionName = :permissionName")
    List<UserGroup> findGroupsWithPermission(@Param("permissionName") String permissionName);

    boolean existsByGroupName(String groupName);

    @Query("SELECT COUNT(m) FROM UserGroup g JOIN g.members m WHERE g.groupId = :groupId")
    long countMembers(@Param("groupId") Long groupId);

    @Query("SELECT g FROM UserGroup g WHERE g.systemGroup = true")
    List<UserGroup> findSystemGroups();

    @Query("SELECT g FROM UserGroup g WHERE g.systemGroup = false")
    List<UserGroup> findCustomGroups();

    // Override to use join fetch
    @Query("SELECT g FROM UserGroup g LEFT JOIN FETCH g.members LEFT JOIN FETCH g.permissions")
    List<UserGroup> findAllWithMembersAndPermissions();
}