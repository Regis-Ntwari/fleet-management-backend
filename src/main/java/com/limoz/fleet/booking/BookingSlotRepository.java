package com.limoz.fleet.booking;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingSlotRepository extends JpaRepository<BookingSlot, Long>, JpaSpecificationExecutor<BookingSlot> {

    @EntityGraph(attributePaths = {"booking", "booking.customer", "line", "category", "vehicle", "vehicle.category", "driver"})
    Optional<BookingSlot> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"booking", "booking.customer", "line", "category", "vehicle", "vehicle.category", "driver"})
    List<BookingSlot> findByBookingIdOrderBySlotNumberAsc(Long bookingId);

    /** Slots holding the vehicle (ASSIGNED / DEPLOYED) that overlap the inclusive date range, excluding one slot. */
    @EntityGraph(attributePaths = {"booking", "booking.customer"})
    @Query("""
            select s from BookingSlot s
            where s.vehicle.id = :vehicleId and s.status in :statuses
              and s.startDate <= :to and s.endDate >= :from
              and (:excludeId is null or s.id <> :excludeId)
            order by s.startDate asc""")
    List<BookingSlot> findOverlappingForVehicle(@Param("vehicleId") Long vehicleId, @Param("from") LocalDate from, @Param("to") LocalDate to,
                                                @Param("statuses") List<SlotStatus> statuses, @Param("excludeId") Long excludeId);

    @EntityGraph(attributePaths = {"booking", "booking.customer"})
    @Query("""
            select s from BookingSlot s
            where s.driver.id = :driverId and s.status in :statuses
              and s.startDate <= :to and s.endDate >= :from
              and (:excludeId is null or s.id <> :excludeId)
            order by s.startDate asc""")
    List<BookingSlot> findOverlappingForDriver(@Param("driverId") Long driverId, @Param("from") LocalDate from, @Param("to") LocalDate to,
                                               @Param("statuses") List<SlotStatus> statuses, @Param("excludeId") Long excludeId);

    /** All slots in the given statuses whose date range overlaps [from, to] (availability calendar, board). */
    @EntityGraph(attributePaths = {"booking", "booking.customer", "line", "category", "vehicle", "vehicle.category", "driver"})
    @Query("""
            select s from BookingSlot s
            where s.status in :statuses and s.startDate <= :to and s.endDate >= :from
            order by s.startDate asc, s.booking.id asc, s.slotNumber asc""")
    List<BookingSlot> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("statuses") List<SlotStatus> statuses);

    /** Slots starting within [from, to] in the given statuses (upcoming jobs). */
    @EntityGraph(attributePaths = {"booking", "booking.customer", "line", "category", "vehicle", "vehicle.category", "driver"})
    @Query("""
            select s from BookingSlot s
            where s.status in :statuses and s.startDate between :from and :to
            order by s.startDate asc, s.booking.id asc, s.slotNumber asc""")
    List<BookingSlot> findStartingBetween(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("statuses") List<SlotStatus> statuses);

    @EntityGraph(attributePaths = {"booking", "booking.customer", "line", "category", "vehicle", "vehicle.category", "driver"})
    @Query("""
            select s from BookingSlot s
            where s.status in :statuses and s.endDate = :date
            order by s.booking.id asc, s.slotNumber asc""")
    List<BookingSlot> findEndingOn(@Param("date") LocalDate date, @Param("statuses") List<SlotStatus> statuses);

    @Query("""
            select s.booking.id as bookingId, s.status as status, count(s) as total
            from BookingSlot s where s.booking.id in :bookingIds
            group by s.booking.id, s.status""")
    List<SlotStatusCount> countByBookingIds(@Param("bookingIds") List<Long> bookingIds);

    interface SlotStatusCount {
        Long getBookingId();
        SlotStatus getStatus();
        long getTotal();
    }
}
