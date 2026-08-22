package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_queue", indexes = {
        @Index(name = "idx_email_queue_status", columnList = "status"),
        @Index(name = "idx_email_queue_next_retry", columnList = "nextRetryAt"),
        @Index(name = "idx_email_queue_created", columnList = "createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String to;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(length = 20)
    private String status; // PENDING, SENT, FAILED

    @Column(name = "retry_count")
    private int retryCount = 0;

    @Column(name = "max_retries")
    private int maxRetries = 5;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "email_type", length = 50)
    private String emailType;

    @Column(name = "priority")
    private Integer priority = 1;

    @Column(name = "attachment_path")
    private String attachmentPath;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "related_entity_type")
    private String relatedEntityType;
}