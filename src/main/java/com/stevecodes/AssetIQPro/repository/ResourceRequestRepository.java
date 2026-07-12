package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.ResourceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRequestRepository extends JpaRepository<ResourceRequest, Long> {

    /**
     * Find all resource requests for a specific user
     */
    List<ResourceRequest> findByUserId(Long userId);

    /**
     * Find all resource requests ordered by request time (newest first)
     */
    List<ResourceRequest> findAllByOrderByRequestTimeDesc();

    /**
     * Find all resource requests with a specific status
     */
    List<ResourceRequest> findByStatus(String status);

    /**
     * Find all resource requests with a specific status ordered by request time
     */
    List<ResourceRequest> findByStatusOrderByRequestTimeDesc(String status);

    /**
     * Count resource requests by status
     */
    long countByStatus(String status);

    /**
     * Find all resource requests for a specific user with a specific status
     */
    List<ResourceRequest> findByUserIdAndStatus(Long userId, String status);

    /**
     * Find all resource requests of a specific type
     */
    List<ResourceRequest> findByResourceType(String resourceType);

    /**
     * Find all resource requests with a specific final status
     */
    List<ResourceRequest> findByFinalStatus(String finalStatus);
}