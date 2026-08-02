package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.AssetDisposalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetDisposalRequestRepository extends JpaRepository<AssetDisposalRequest, Long> {

    List<AssetDisposalRequest> findByStatus(String status);

    List<AssetDisposalRequest> findByAssetId(Integer assetId);

    List<AssetDisposalRequest> findByRequestedBy(Long userId);

    // ✅ Use Integer for assetId (matches Asset entity)
    boolean existsByAssetIdAndStatusIn(Integer assetId, List<String> statuses);

    @Query("SELECT r FROM AssetDisposalRequest r WHERE r.assetId = :assetId AND r.status IN :statuses")
    List<AssetDisposalRequest> findByAssetIdAndStatusIn(@Param("assetId") Integer assetId,
                                                        @Param("statuses") List<String> statuses);
}