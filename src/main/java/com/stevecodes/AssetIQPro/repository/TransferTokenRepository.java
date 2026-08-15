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

    // FIXED: Changed transferId from Integer to Long
    List<TransferToken> findByTransferId(Long transferId);

    // FIXED: Changed transferId from Integer to Long
    List<TransferToken> findByTransferIdAndIsUsed(Long transferId, Boolean isUsed);

    List<TransferToken> findByIsUsedFalseAndExpiresAtBefore(LocalDateTime expiryDate);

    void deleteByIsUsedFalseAndExpiresAtBefore(LocalDateTime expiryDate);

    // FIXED: Changed transferId from Integer to Long
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TransferToken t " +
            "WHERE t.transferId = :transferId AND t.signerEmployeeId = :signerEmployeeId " +
            "AND t.isUsed = false AND t.expiresAt > :now")
    boolean hasValidTokenForSigner(@Param("transferId") Long transferId,
                                   @Param("signerEmployeeId") Long signerEmployeeId,
                                   @Param("now") LocalDateTime now);

    @Query("SELECT t FROM TransferToken t WHERE t.isUsed = false AND t.expiresAt BETWEEN :now AND :futureDate")
    List<TransferToken> findTokensExpiringSoon(@Param("now") LocalDateTime now,
                                               @Param("futureDate") LocalDateTime futureDate);

    // FIXED: Changed transferId from Integer to Long
    Optional<TransferToken> findTopByTransferIdAndSignerEmployeeIdOrderByCreatedAtDesc(Long transferId, Long signerEmployeeId);

    // FIXED: Changed transferId from Integer to Long
    long countByTransferIdAndIsUsedFalse(Long transferId);

    // FIXED: Changed transferId from Integer to Long
    long countByTransferIdAndIsUsedTrue(Long transferId);

    // FIXED: Changed transferId from Integer to Long
    @Query("SELECT COUNT(t) FROM TransferToken t WHERE t.transferId = :transferId")
    long countTokensByTransferId(@Param("transferId") Long transferId);

    // FIXED: Changed transferId from Integer to Long
    @Query("SELECT CASE WHEN COUNT(t) = SUM(CASE WHEN t.isUsed = true THEN 1 ELSE 0 END) " +
            "THEN true ELSE false END FROM TransferToken t WHERE t.transferId = :transferId")
    boolean areAllTokensUsedForTransfer(@Param("transferId") Long transferId);

    List<TransferToken> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<TransferToken> findBySignerEmail(String signerEmail);

    // Additional helper methods
    @Query("SELECT t FROM TransferToken t WHERE t.transferId = :transferId ORDER BY t.createdAt DESC")
    List<TransferToken> findTokensByTransferIdDesc(@Param("transferId") Long transferId);

    @Query("SELECT COUNT(t) FROM TransferToken t WHERE t.transferId = :transferId AND t.isUsed = false")
    long countUnusedTokensForTransfer(@Param("transferId") Long transferId);

    @Query("SELECT COUNT(t) FROM TransferToken t WHERE t.transferId = :transferId AND t.isUsed = true")
    long countUsedTokensForTransfer(@Param("transferId") Long transferId);
}