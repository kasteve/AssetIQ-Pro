package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByName(String name);

    @Query("SELECT c FROM Company c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Company> searchCompanies(@Param("searchTerm") String searchTerm);

    List<Company> findAllByOrderByNameAsc();

    boolean existsByName(String name);

    @Query("SELECT c.name FROM Company c ORDER BY c.name")
    List<String> findAllCompanyNames();
}