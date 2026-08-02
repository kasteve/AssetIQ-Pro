package com.stevecodes.AssetIQPro.dto;

import com.stevecodes.AssetIQPro.entity.RequestSLATracking;
import com.stevecodes.AssetIQPro.entity.SLAConfiguration;
import com.stevecodes.AssetIQPro.entity.SLAEscalationHistory;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class SlaTrackingDetailDTO {

    // Tracking info
    private Long trackingId;
    private Long requestId;
    private String requestType;
    private Integer slaConfigId;
    private LocalDateTime slaStartedAt;
    private LocalDateTime slaDueAt;
    private String status;
    private Integer breachesCount;
    private Integer escalationCount;
    private Long escalatedTo;
    private LocalDateTime completedAt;
    private LocalDateTime breachedAt;
    private LocalDateTime lastReminderSentAt;
    private Double percentageComplete;

    // Config info
    private String configName;
    private Integer slaHours;
    private String configDescription;

    // Escalations
    private List<EscalationDetailDTO> escalations = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class EscalationDetailDTO {
        private Long escalationId;
        private Integer escalationLevel;
        private Long escalatedTo;
        private Long escalatedBy;
        private String escalatedToName;
        private String escalatedByName;
        private LocalDateTime escalatedAt;
        private Boolean actionTaken;
        private String reason;
    }

    public static SlaTrackingDetailDTO fromTracking(RequestSLATracking tracking,
                                                    SLAConfiguration config,
                                                    List<SLAEscalationHistory> escalations) {
        SlaTrackingDetailDTO dto = new SlaTrackingDetailDTO();

        // Tracking info
        dto.setTrackingId(tracking.getTrackingId());
        dto.setRequestId(tracking.getRequestId());
        dto.setRequestType(tracking.getRequestType());
        dto.setSlaConfigId(tracking.getSlaConfigId());
        dto.setSlaStartedAt(tracking.getSlaStartedAt());
        dto.setSlaDueAt(tracking.getSlaDueAt());
        dto.setStatus(tracking.getStatus());
        dto.setBreachesCount(tracking.getBreachesCount());
        dto.setEscalationCount(tracking.getEscalationCount());
        dto.setEscalatedTo(tracking.getEscalatedTo());
        dto.setCompletedAt(tracking.getCompletedAt());
        dto.setLastReminderSentAt(tracking.getLastReminderSentAt());

        // Calculate percentage
        if (tracking.getSlaStartedAt() != null && tracking.getSlaDueAt() != null) {
            long totalHours = java.time.Duration.between(tracking.getSlaStartedAt(), tracking.getSlaDueAt()).toHours();
            long elapsedHours = java.time.Duration.between(tracking.getSlaStartedAt(), LocalDateTime.now()).toHours();
            double percentage = totalHours > 0 ? (double) elapsedHours / totalHours * 100 : 0;
            dto.setPercentageComplete(Math.min(percentage, 100));
        }

        // Config info
        if (config != null) {
            dto.setConfigName(config.getConfigName());
            dto.setSlaHours(config.getSlaHours());
            dto.setConfigDescription(config.getDescription());
        }

        // Escalations
        if (escalations != null) {
            for (SLAEscalationHistory esc : escalations) {
                EscalationDetailDTO escDto = new EscalationDetailDTO();
                escDto.setEscalationId(esc.getEscalationId());
                escDto.setEscalationLevel(esc.getEscalationLevel());
                escDto.setEscalatedTo(esc.getEscalatedTo());
                escDto.setEscalatedBy(esc.getEscalatedBy());
                escDto.setEscalatedAt(esc.getEscalatedAt());
                escDto.setActionTaken(esc.getActionTaken());
                escDto.setReason(esc.getReason());

                // Get names from the entities if available
                if (esc.getEscalatedToUser() != null) {
                    escDto.setEscalatedToName(esc.getEscalatedToUser().getFullName());
                }
                if (esc.getEscalatedByUser() != null) {
                    escDto.setEscalatedByName(esc.getEscalatedByUser().getFullName());
                }

                dto.getEscalations().add(escDto);
            }
        }

        return dto;
    }
}