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

    @Query("SELECT b FROM Booking b WHERE b.roomId = :roomId AND b.startTime >= :startDate AND b.status IN :statuses ORDER BY b.startTime")
    List<Booking> findActiveBookingsForRoom(@Param("roomId") Long roomId,
                                            @Param("startDate") LocalDateTime startDate,
                                            @Param("statuses") List<BookingStatus> statuses);

    @Query("SELECT b FROM Booking b WHERE b.startTime <= :time AND b.endTime >= :time AND b.status IN :statuses")
    List<Booking> findActiveBookingsAtTime(@Param("time") LocalDateTime time,
                                           @Param("statuses") List<BookingStatus> statuses);

    @Query("SELECT b FROM Booking b WHERE b.endTime < :now AND b.status = 'BOOKED'")
    List<Booking> findExpiredBookings(@Param("now") LocalDateTime now);

    @Query("SELECT b FROM Booking b WHERE b.roomId = :roomId AND b.startTime BETWEEN :startDate AND :endDate AND b.status IN ('BOOKED', 'CONFIRMED', 'PENDING', 'ACTIVE')")
    List<Booking> findBookingsForRoomInDateRange(@Param("roomId") Long roomId,
                                                 @Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    @Query("SELECT b FROM Booking b WHERE b.roomId = :roomId AND b.status IN :statuses AND b.startTime BETWEEN :startDate AND :endDate")
    List<Booking> findBookingsForRoomInDateRangeWithStatuses(@Param("roomId") Long roomId,
                                                             @Param("startDate") LocalDateTime startDate,
                                                             @Param("endDate") LocalDateTime endDate,
                                                             @Param("statuses") List<BookingStatus> statuses);

    List<Booking> findTop5ByOrderByStartTimeDesc();

    long countByStatus(BookingStatus status);

    @Query("SELECT b.roomId, COUNT(b) FROM Booking b WHERE b.status = 'BOOKED' GROUP BY b.roomId")
    List<Object[]> countBookingsByRoom();

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.startTime >= :startOfDay AND b.startTime < :endOfDay AND b.status = 'BOOKED'")
    long countBookingsToday(@Param("startOfDay") LocalDateTime startOfDay,
                            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' ORDER BY b.createdAt DESC")
    List<Booking> findPendingBookings();

    @Query("SELECT b FROM Booking b WHERE b.signoutToken = :token")
    Booking findBySignoutToken(@Param("token") String token);

    @Query("SELECT b FROM Booking b WHERE b.roomId = :roomId AND b.status IN :statuses AND b.startTime < :now AND b.endTime > :now")
    List<Booking> findCurrentBookingsForRoom(@Param("roomId") Long roomId,
                                             @Param("now") LocalDateTime now,
                                             @Param("statuses") List<BookingStatus> statuses);

    @Query("SELECT b FROM Booking b WHERE b.roomId = :roomId AND b.status = 'BOOKED' AND b.startTime >= :startDate ORDER BY b.startTime")
    List<Booking> findUpcomingBookingsForRoom(@Param("roomId") Long roomId,
                                              @Param("startDate") LocalDateTime startDate);

    // ✅ UPDATED: Find pending server room bookings - checks both room_type and room_name
    @Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' AND " +
            "(b.roomType = 'Server Room' OR b.roomType = 'Server' OR " +
            "LOWER(b.roomName) LIKE LOWER('%server%')) " +
            "ORDER BY b.createdAt DESC")
    List<Booking> findPendingServerRoomBookings();

    // ✅ UPDATED: Find approved server room bookings
    @Query("SELECT b FROM Booking b WHERE b.status = 'BOOKED' AND " +
            "(b.roomType = 'Server Room' OR b.roomType = 'Server' OR " +
            "LOWER(b.roomName) LIKE LOWER('%server%')) " +
            "ORDER BY b.startTime ASC")
    List<Booking> findApprovedServerRoomBookings();

    @Query("SELECT b FROM Booking b WHERE b.roomId = :roomId AND b.startTime BETWEEN :startDate AND :endDate")
    List<Booking> findBookingsForRoomByDateRange(@Param("roomId") Long roomId,
                                                 @Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    List<Booking> findTop5ByUserId(Long userId);

    @Query("SELECT b FROM Booking b WHERE b.userId = :userId ORDER BY b.startTime DESC")
    List<Booking> findTop5BookingsByUserId(@Param("userId") Long userId);
}