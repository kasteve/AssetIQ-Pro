package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.ResourceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRequestRepository extends JpaRepository<ResourceRequest, Long> {

    List<ResourceRequest> findAllByOrderByRequestTimeDesc();

    List<ResourceRequest> findByUserId(Long userId);

    List<ResourceRequest> findByStatus(String status);

    long countByStatus(String status);

    Optional<ResourceRequest> findBySigningToken(String token);
}