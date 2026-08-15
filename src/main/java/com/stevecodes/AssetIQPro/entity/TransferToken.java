package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "TransferTokens")
@Data
@NoArgsConstructor
public class TransferToken {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "TokenId", columnDefinition = "uniqueidentifier")
    private UUID tokenId;

    @Column(name = "TransferId", nullable = false)
    private Long transferId;  // FIXED: Changed from Integer to Long

    @Column(name = "SignerEmployeeId")
    private Long signerEmployeeId;

    @Column(name = "SignerEmail", nullable = false, length = 255)
    private String signerEmail;

    @Column(name = "SignerRole", nullable = false, length = 50)
    private String signerRole;

    @Column(name = "Token", nullable = false, unique = true, length = 100)
    private String token;

    @Column(name = "IsUsed", nullable = false)
    private Boolean isUsed = false;

    @Column(name = "ExpiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    // FIXED: Constructor with Long transferId
    public TransferToken(Long transferId, Long signerEmployeeId, String signerEmail,
                         String signerRole, String token, LocalDateTime expiresAt) {
        this.transferId = transferId;
        this.signerEmployeeId = signerEmployeeId;
        this.signerEmail = signerEmail;
        this.signerRole = signerRole;
        this.token = token;
        this.expiresAt = expiresAt;
        this.isUsed = false;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (token == null) {
            token = UUID.randomUUID().toString();
        }
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusHours(24);
        }
        if (isUsed == null) {
            isUsed = false;
        }
    }

    // Helper methods
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isValid() {
        return !isUsed && !isExpired();
    }
}