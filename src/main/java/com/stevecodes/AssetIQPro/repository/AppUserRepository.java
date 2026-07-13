package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    @Query("SELECT u FROM AppUser u WHERE u.username = :username OR u.email = :email")
    Optional<AppUser> findByUsernameOrEmail(@Param("username") String username, @Param("email") String email);

    List<AppUser> findByDepartment(String department);

    List<AppUser> findByActiveTrue();

    List<AppUser> findByActiveFalse();

    long countByActiveTrue();

    long countByActiveFalse();

    @Query("SELECT u FROM AppUser u JOIN u.permissions p WHERE p.permissionName = :permissionName")
    List<AppUser> findUsersWithPermission(@Param("permissionName") String permissionName);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM AppUser u JOIN u.permissions p WHERE u.userId = :userId AND p.permissionName = :permissionName")
    boolean hasPermission(@Param("userId") Long userId, @Param("permissionName") String permissionName);

    @Query("SELECT u FROM AppUser u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<AppUser> searchUsers(@Param("searchTerm") String searchTerm);

    List<AppUser> findByRole(String role);

    Optional<AppUser> findByPasswordResetToken(String token);
}