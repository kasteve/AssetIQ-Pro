package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_history")
@Getter
@Setter
@NoArgsConstructor
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public PasswordHistory(Long userId, String passwordHash) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.changedAt = LocalDateTime.now();
    }
}