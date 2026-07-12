package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Integer> {

    Optional<Department> findByName(String name);

    @Query("SELECT d FROM Department d WHERE LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Department> searchDepartments(@Param("searchTerm") String searchTerm);

    List<Department> findAllByOrderByNameAsc();

    boolean existsByName(String name);

    @Query("SELECT d.name FROM Department d ORDER BY d.name")
    List<String> findAllDepartmentNames();

    @Query("SELECT d FROM Department d WHERE d.manager IS NOT NULL")
    List<Department> findDepartmentsWithManager();

    @Query("SELECT d FROM Department d WHERE d.manager IS NULL")
    List<Department> findDepartmentsWithoutManager();
}