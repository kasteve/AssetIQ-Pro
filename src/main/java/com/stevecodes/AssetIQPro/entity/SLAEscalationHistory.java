package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sla_escalation_history")
@Data
@NoArgsConstructor
public class SLAEscalationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "escalation_id")
    private Long escalationId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "request_type", nullable = false, length = 50)
    private String requestType;

    @Column(name = "sla_rule_id", nullable = false)
    private Integer slaRuleId;

    @Column(name = "escalation_level")
    private Integer escalationLevel = 1;

    @Column(name = "escalated_to", nullable = false)
    private Long escalatedTo;

    @Column(name = "escalated_by", nullable = false)
    private Long escalatedBy;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "notification_sent")
    private Boolean notificationSent = false;

    @Column(name = "action_taken")
    private Boolean actionTaken = false;

    @Column(name = "action_taken_at")
    private LocalDateTime actionTakenAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalated_to", insertable = false, updatable = false)
    private AppUser escalatedToUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalated_by", insertable = false, updatable = false)
    private AppUser escalatedByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_rule_id", insertable = false, updatable = false)
    private DisposalSLARule slaRule;

    public void setReason(String s) {
    }
}