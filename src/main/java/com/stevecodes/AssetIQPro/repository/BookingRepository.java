package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.Booking;
import com.stevecodes.AssetIQPro.entity.Booking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByRoomId(Long roomId);

    @Query("SELECT b FROM Booking b WHERE b.roomId = :roomId AND b.startTime >= :startDate AND b.status = 'BOOKED' ORDER BY b.startTime")
    List<Booking> findActiveBookingsForRoom(@Param("roomId") Long roomId,
                                            @Param("startDate") LocalDateTime startDate);

    @Query("SELECT b FROM Booking b WHERE b.startTime <= :time AND b.endTime >= :time AND b.status = 'BOOKED'")
    List<Booking> findActiveBookingsAtTime(@Param("time") LocalDateTime time);

    @Query("SELECT b FROM Booking b WHERE b.endTime < :now AND b.status = 'BOOKED'")
    List<Booking> findExpiredBookings(@Param("now") LocalDateTime now);

    @Query("SELECT b FROM Booking b WHERE b.roomId = :roomId AND b.status = 'BOOKED' AND b.startTime BETWEEN :startDate AND :endDate")
    List<Booking> findBookingsForRoomInDateRange(@Param("roomId") Long roomId,
                                                 @Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    List<Booking> findTop5ByOrderByStartTimeDesc();

    long countByStatus(BookingStatus status);

    @Query("SELECT b.roomId, COUNT(b) FROM Booking b WHERE b.status = 'BOOKED' GROUP BY b.roomId")
    List<Object[]> countBookingsByRoom();

    // FIXED: Use @Query for today's bookings count
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.startTime >= :startOfDay AND b.startTime < :endOfDay AND b.status = 'BOOKED'")
    long countBookingsToday(@Param("startOfDay") LocalDateTime startOfDay,
                            @Param("endOfDay") LocalDateTime endOfDay);
}