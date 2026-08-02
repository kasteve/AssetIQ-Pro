package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "request_sla_tracking")
@Data
@NoArgsConstructor
public class RequestSLATracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tracking_id")
    private Long trackingId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "request_type", nullable = false, length = 50)
    private String requestType;

    @Column(name = "sla_config_id", nullable = false)
    private Integer slaConfigId;

    @Column(name = "sla_started_at", nullable = false)
    private LocalDateTime slaStartedAt;

    @Column(name = "sla_due_at", nullable = false)
    private LocalDateTime slaDueAt;

    @Column(name = "status")
    private String status = "IN_PROGRESS";

    @Column(name = "breaches_count")
    private Integer breachesCount = 0;

    @Column(name = "last_reminder_sent_at")
    private LocalDateTime lastReminderSentAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "escalated_to")
    private Long escalatedTo;

    @Column(name = "escalation_count")
    private Integer escalationCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_config_id", insertable = false, updatable = false)
    private SLAConfiguration slaConfiguration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalated_to", insertable = false, updatable = false)
    private AppUser escalatedToUser;

    public String getStatusDisplay() {
        if (status == null) return "";
        switch (status) {
            case "IN_PROGRESS": return "In Progress";
            case "BREACHED": return "Breached";
            case "COMPLETED": return "Completed";
            case "ESCALATED": return "Escalated";
            default: return status;
        }
    }

    public String getStatusColor() {
        if (status == null) return "secondary";
        switch (status) {
            case "IN_PROGRESS": return "primary";
            case "BREACHED": return "danger";
            case "COMPLETED": return "success";
            case "ESCALATED": return "warning";
            default: return "secondary";
        }
    }

    public long getHoursRemaining() {
        if (slaDueAt == null) return 0;
        return java.time.Duration.between(LocalDateTime.now(), slaDueAt).toHours();
    }

    public boolean isBreached() {
        return "BREACHED".equals(status) || (slaDueAt != null && slaDueAt.isBefore(LocalDateTime.now()));
    }

    public double getPercentageComplete() {
        if (slaStartedAt == null || slaDueAt == null) return 0;
        long total = java.time.Duration.between(slaStartedAt, slaDueAt).toHours();
        long elapsed = java.time.Duration.between(slaStartedAt, LocalDateTime.now()).toHours();
        if (total <= 0) return 0;
        return Math.min(100, (double) elapsed / total * 100);
    }

    // ✅ FIXED: These methods are now properly implemented
    public void setEscalationCount(int escalationCount) {
        this.escalationCount = escalationCount;
    }

    public int getEscalationCount() {
        return escalationCount != null ? escalationCount : 0;
    }
}