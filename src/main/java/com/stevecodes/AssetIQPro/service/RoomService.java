package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Room;
import com.stevecodes.AssetIQPro.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    public Room getRoomById(Long id) {
        return roomRepository.findById(id).orElse(null);
    }

    public Room createRoom(Room room) {
        return roomRepository.save(room);
    }

    public Room updateRoom(Long id, String roomName, String roomType, String status) {
        Room room = getRoomById(id);
        room.setRoomName(roomName);
        room.setRoomType(roomType);
        room.setStatus(Room.RoomStatus.valueOf(status));
        return roomRepository.save(room);
    }

    public void deleteRoom(Long id) {
        roomRepository.deleteById(id);
    }
}