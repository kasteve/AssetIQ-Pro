package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_history")
@Data
@NoArgsConstructor
public class AssetHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "asset_id", nullable = false)
    private Integer assetId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @CreationTimestamp
    @Column(name = "event_date", updatable = false)
    private LocalDateTime eventDate;

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(name = "details", columnDefinition = "NVARCHAR(MAX)")
    private String details;

    @Column(name = "transfer_id")
    private Long transferId;

    @Column(name = "request_id")
    private Long requestId;

    // ============================================
    // Event Type Constants
    // ============================================
    public static final String EVENT_TRANSFER = "TRANSFER";
    public static final String EVENT_PROCUREMENT = "PROCUREMENT";
    public static final String EVENT_EOL = "EOL";
    public static final String EVENT_WARRANTY = "WARRANTY";
    public static final String EVENT_REPAIR = "REPAIR";
    public static final String EVENT_RETIREMENT = "RETIREMENT";
    public static final String EVENT_ASSIGNMENT = "ASSIGNMENT";
    public static final String EVENT_RETURN = "RETURN";

    // ============================================
    // Builder Pattern for Easy Creation
    // ============================================
    public static AssetHistory create(Integer assetId, String eventType, Long performedBy, String details) {
        AssetHistory history = new AssetHistory();
        history.setAssetId(assetId);
        history.setEventType(eventType);
        history.setPerformedBy(performedBy);
        history.setDetails(details);
        return history;
    }

    public static AssetHistory createWithTransfer(Integer assetId, Long performedBy, String details, Long transferId) {
        AssetHistory history = create(assetId, EVENT_TRANSFER, performedBy, details);
        history.setTransferId(transferId);
        return history;
    }

    public static AssetHistory createWithRequest(Integer assetId, Long performedBy, String details, Long requestId) {
        AssetHistory history = create(assetId, EVENT_PROCUREMENT, performedBy, details);
        history.setRequestId(requestId);
        return history;
    }
}
