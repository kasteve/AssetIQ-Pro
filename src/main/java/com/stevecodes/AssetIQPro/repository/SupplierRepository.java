package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Integer> {

    Optional<Supplier> findByName(String name);

    Optional<Supplier> findByEmail(String email);

    @Query("SELECT s FROM Supplier s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(s.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(s.contact) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Supplier> searchSuppliers(@Param("searchTerm") String searchTerm);

    List<Supplier> findAllByOrderByNameAsc();

    boolean existsByName(String name);

    boolean existsByEmail(String email);

    @Query("SELECT s.name FROM Supplier s ORDER BY s.name")
    List<String> findAllSupplierNames();
}