package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "request_sla_tracking")
@Data
@NoArgsConstructor
public class RequestSLATracking {

    // Status Constants
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_BREACHED = "BREACHED";
    public static final String STATUS_ESCALATED = "ESCALATED";

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
    private String status = STATUS_IN_PROGRESS;

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

    // ============================================
    // STATUS DISPLAY HELPERS
    // ============================================

    public String getStatusDisplay() {
        if (status == null) return "";
        switch (status) {
            case STATUS_IN_PROGRESS:
                return "In Progress";
            case STATUS_COMPLETED:
                return "Completed";
            case STATUS_BREACHED:
                return "Breached";
            case STATUS_ESCALATED:
                return "Escalated";
            default:
                return status;
        }
    }

    public String getStatusColor() {
        if (status == null) return "secondary";
        switch (status) {
            case STATUS_IN_PROGRESS:
                return "primary";
            case STATUS_COMPLETED:
                return "success";
            case STATUS_BREACHED:
                return "danger";
            case STATUS_ESCALATED:
                return "warning";
            default:
                return "secondary";
        }
    }

    // ============================================
    // CALCULATION HELPERS
    // ============================================

    public long getHoursRemaining() {
        if (slaDueAt == null) return 0;
        return ChronoUnit.HOURS.between(LocalDateTime.now(), slaDueAt);
    }

    public long getHoursElapsed() {
        if (slaStartedAt == null) return 0;
        return ChronoUnit.HOURS.between(slaStartedAt, LocalDateTime.now());
    }

    public long getTotalHours() {
        if (slaStartedAt == null || slaDueAt == null) return 0;
        return ChronoUnit.HOURS.between(slaStartedAt, slaDueAt);
    }

    public boolean isBreached() {
        return STATUS_BREACHED.equals(status) ||
                (slaDueAt != null && slaDueAt.isBefore(LocalDateTime.now()));
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    public boolean isEscalated() {
        return STATUS_ESCALATED.equals(status);
    }

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(status);
    }

    public double getPercentageComplete() {
        if (slaStartedAt == null || slaDueAt == null) return 0;
        long total = ChronoUnit.HOURS.between(slaStartedAt, slaDueAt);
        long elapsed = ChronoUnit.HOURS.between(slaStartedAt, LocalDateTime.now());
        if (total <= 0) return 0;
        return Math.min(100, (double) elapsed / total * 100);
    }

    // ============================================
    // SETTERS WITH NULL SAFETY
    // ============================================

    public void setEscalationCount(int escalationCount) {
        this.escalationCount = escalationCount;
    }

    public int getEscalationCount() {
        return escalationCount != null ? escalationCount : 0;
    }

    public void setBreachesCount(int breachesCount) {
        this.breachesCount = breachesCount;
    }

    public int getBreachesCount() {
        return breachesCount != null ? breachesCount : 0;
    }

    // ============================================
    // INCREMENT METHODS
    // ============================================

    public void incrementEscalationCount() {
        this.escalationCount = getEscalationCount() + 1;
    }

    public void incrementBreachesCount() {
        this.breachesCount = getBreachesCount() + 1;
    }
}