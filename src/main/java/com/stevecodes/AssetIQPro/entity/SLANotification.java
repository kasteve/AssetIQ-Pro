package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sla_notifications")
@Data
@NoArgsConstructor
public class SLANotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "request_type", nullable = false, length = 50)
    private String requestType;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType; // PENDING_APPROVAL, ESCALATION, BREACH, REMINDER

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "sent_via")
    private String sentVia = "EMAIL"; // EMAIL, IN_APP, BOTH

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "message", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String message;

    @Column(name = "link", length = 500)
    private String link;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", insertable = false, updatable = false)
    private AppUser recipient;

    public boolean isRead() {
        return readAt != null;
    }
}