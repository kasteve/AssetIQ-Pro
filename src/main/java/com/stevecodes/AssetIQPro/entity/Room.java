package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tbl_rooms")
@Data
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "room_name", nullable = false)
    private String roomName;

    @Column(name = "room_type")
    private String roomType; // Boardroom, Meeting Room, Server Room, Office, etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RoomStatus status = RoomStatus.AVAILABLE;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "floor")
    private String floor;

    @Column(name = "building")
    private String building;

    @Column(name = "has_projector")
    private Boolean hasProjector = false;

    @Column(name = "has_whiteboard")
    private Boolean hasWhiteboard = false;

    @Column(name = "has_video_conferencing")
    private Boolean hasVideoConferencing = false;

    @Column(name = "description")
    private String description;

    public enum RoomStatus {
        AVAILABLE, OCCUPIED, MAINTENANCE, RESERVED
    }

    public String getName() {
        return roomName;
    }

    public String getRoomNumber() {
        return roomName;
    }
}