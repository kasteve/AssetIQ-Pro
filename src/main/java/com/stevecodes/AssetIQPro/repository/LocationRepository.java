package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Integer> {

    Optional<Location> findByName(String name);

    @Query("SELECT l FROM Location l WHERE LOWER(l.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(l.city) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(l.country) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Location> searchLocations(@Param("searchTerm") String searchTerm);

    List<Location> findAllByOrderByNameAsc();

    List<Location> findByCity(String city);

    List<Location> findByCountry(String country);

    boolean existsByName(String name);

    @Query("SELECT l.name FROM Location l ORDER BY l.name")
    List<String> findAllLocationNames();
}