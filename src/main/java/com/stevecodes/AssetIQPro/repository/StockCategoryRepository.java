package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.StockCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockCategoryRepository extends JpaRepository<StockCategory, Long> {

    Optional<StockCategory> findByName(String name);

    List<StockCategory> findAllByOrderByNameAsc();

    boolean existsByName(String name);
}