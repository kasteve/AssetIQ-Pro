package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.TransferToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferTokenRepository extends JpaRepository<TransferToken, UUID> {

    Optional<TransferToken> findByToken(String token);

    List<TransferToken> findByTransferId(Integer transferId);

    List<TransferToken> findByTransferIdAndIsUsed(Integer transferId, Boolean isUsed);

    List<TransferToken> findByIsUsedFalseAndExpiresAtBefore(LocalDateTime expiryDate);

    void deleteByIsUsedFalseAndExpiresAtBefore(LocalDateTime expiryDate);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TransferToken t " +
            "WHERE t.transferId = :transferId AND t.signerEmployeeId = :signerEmployeeId " +
            "AND t.isUsed = false AND t.expiresAt > :now")
    boolean hasValidTokenForSigner(@Param("transferId") Integer transferId,
                                   @Param("signerEmployeeId") Long signerEmployeeId,
                                   @Param("now") LocalDateTime now);

    @Query("SELECT t FROM TransferToken t WHERE t.isUsed = false AND t.expiresAt BETWEEN :now AND :futureDate")
    List<TransferToken> findTokensExpiringSoon(@Param("now") LocalDateTime now,
                                               @Param("futureDate") LocalDateTime futureDate);

    Optional<TransferToken> findTopByTransferIdAndSignerEmployeeIdOrderByCreatedAtDesc(Integer transferId, Long signerEmployeeId);

    long countByTransferIdAndIsUsedFalse(Integer transferId);

    long countByTransferIdAndIsUsedTrue(Integer transferId);

    @Query("SELECT COUNT(t) FROM TransferToken t WHERE t.transferId = :transferId")
    long countTokensByTransferId(@Param("transferId") Integer transferId);

    @Query("SELECT CASE WHEN COUNT(t) = SUM(CASE WHEN t.isUsed = true THEN 1 ELSE 0 END) " +
            "THEN true ELSE false END FROM TransferToken t WHERE t.transferId = :transferId")
    boolean areAllTokensUsedForTransfer(@Param("transferId") Integer transferId);

    List<TransferToken> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<TransferToken> findBySignerEmail(String signerEmail);
}