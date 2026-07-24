package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "AuditLogs")
@Data
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AuditId")
    private Long auditId;

    @Column(name = "UserId")
    private Long userId;

    @Column(name = "Action")
    private String action;

    @Column(name = "EntityType")
    private String entityType;

    @Column(name = "EntityId")
    private Integer entityId;

    @CreationTimestamp
    @Column(name = "Timestamp")
    private LocalDateTime timestamp;

    @Column(name = "Details")
    private String details;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    // ============================================
    // Transient field for username (not stored in DB)
    // ============================================
    @Transient
    private String username;

    public AuditLog(Long userId, String action, String details) {
        this.userId = userId;
        this.action = action;
        this.details = details;
        this.timestamp = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    // ============================================
    // FIXED: Returns the username
    // ============================================
    public String getUsername() {
        return username != null ? username : (userId != null ? "User #" + userId : "SYSTEM");
    }

    // ============================================
    // Setter for username (to be set by service layer)
    // ============================================
    public void setUsername(String username) {
        this.username = username;
    }
}