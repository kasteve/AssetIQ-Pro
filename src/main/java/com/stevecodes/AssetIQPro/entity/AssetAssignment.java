package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "AssetAssignments")
@Data
@NoArgsConstructor
public class AssetAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignmentId")
    private Integer assignmentId;

    @ManyToOne
    @JoinColumn(name = "assetId")
    private Asset asset;

    @ManyToOne
    @JoinColumn(name = "userId")
    private AppUser user;

    @Column(name = "assignedAt")
    private LocalDateTime assignedAt;

    @Column(name = "returnedAt")
    private LocalDateTime returnedAt;

    @Column(name = "notes")
    private String notes;

    public AssetAssignment(Asset asset, AppUser user, String notes) {
        this.asset = asset;
        this.user = user;
        this.notes = notes;
        this.assignedAt = LocalDateTime.now();
    }
}