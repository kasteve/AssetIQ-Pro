package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.ResourceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT rr FROM ResourceRequest rr WHERE rr.userId = :userId ORDER BY rr.requestTime DESC")
    List<ResourceRequest> findRequestsForUser(@Param("userId") Long userId);

    //  Find requests where user is the requester
    List<ResourceRequest> findByUserIdOrderByRequestTimeDesc(Long userId);
}