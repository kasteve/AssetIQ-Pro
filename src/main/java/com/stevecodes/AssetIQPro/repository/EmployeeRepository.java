package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmailAddress(String emailAddress);

    Optional<Employee> findByStaffId(String staffId);

    @Query("SELECT e FROM Employee e WHERE e.user.userId = :userId")
    Optional<Employee> findByUserId(@Param("userId") Long userId);

    List<Employee> findByDepartment_DepartmentId(Integer departmentId);

    @Query("SELECT e FROM Employee e WHERE LOWER(e.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(e.surName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(e.emailAddress) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Employee> searchEmployees(@Param("searchTerm") String searchTerm);

    @Query("SELECT e FROM Employee e ORDER BY e.firstName ASC, e.surName ASC")
    List<Employee> findAllOrderedByName();

    boolean existsByEmailAddress(String emailAddress);

    boolean existsByStaffId(String staffId);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.department.departmentId = :departmentId")
    long countByDepartmentId(@Param("departmentId") Integer departmentId);

    // ✅ NEW: Get user_id directly from employee table (native query)
    @Query(value = "SELECT user_id FROM dbo.Employees WHERE EmployeeId = :employeeId", nativeQuery = true)
    Long findUserIdByEmployeeId(@Param("employeeId") Long employeeId);

    // ✅ NEW: Get employee by user_id directly (native query)
    @Query(value = "SELECT * FROM dbo.Employees WHERE user_id = :userId", nativeQuery = true)
    Employee findByUserIdNative(@Param("userId") Long userId);

    // ✅ NEW: Find employees with user_id NOT NULL (who have user accounts)
    @Query("SELECT e FROM Employee e WHERE e.user IS NOT NULL")
    List<Employee> findEmployeesWithUserAccounts();

    // ✅ NEW: Count employees with user accounts
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.user IS NOT NULL")
    long countEmployeesWithUserAccounts();
}