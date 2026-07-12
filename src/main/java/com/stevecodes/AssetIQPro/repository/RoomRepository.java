package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.entity.Room.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByStatus(RoomStatus status);

    @Query("SELECT r FROM Room r WHERE r.roomId NOT IN (" +
            "SELECT b.roomId FROM Booking b WHERE b.startTime <= :time AND b.endTime >= :time " +
            "AND b.status = 'BOOKED') AND r.status = 'AVAILABLE'")
    List<Room> findAvailableRooms(@Param("time") LocalDateTime time);

    @Query("SELECT r FROM Room r WHERE r.roomType = :roomType AND r.status = 'AVAILABLE'")
    List<Room> findAvailableRoomsByType(@Param("roomType") String roomType);

    List<Room> findByRoomType(String roomType);

    @Query("SELECT r FROM Room r ORDER BY r.roomName")
    List<Room> findAllOrderedByName();

    boolean existsByRoomName(String roomName);
}