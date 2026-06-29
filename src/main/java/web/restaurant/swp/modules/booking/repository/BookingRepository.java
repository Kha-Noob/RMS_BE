package web.restaurant.swp.modules.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.booking.model.Booking;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByBranchIdAndBookingTimeBetween(String branchId, java.time.LocalDateTime start, java.time.LocalDateTime end);
    List<Booking> findByBranchIdAndStatusNotAndBookingTimeBetween(
            String branchId, String status, java.time.LocalDateTime start, java.time.LocalDateTime end);
    List<Booking> findByCustomerPhoneOrderByBookingTimeDesc(String phone);
    List<Booking> findByCustomerEmailOrderByBookingTimeDesc(String email);
    List<Booking> findByCustomerPhoneAndStatusInAndBookingTimeBetween(
            String phone, java.util.Collection<String> statuses, java.time.LocalDateTime start, java.time.LocalDateTime end);
    List<Booking> findByBranchIdAndStatusNotInAndBookingTimeBetween(
            String branchId, java.util.Collection<String> statuses, java.time.LocalDateTime start, java.time.LocalDateTime end);
    List<Booking> findByEventId(Long eventId);
    List<Booking> findByEventIdIn(List<Long> eventIds);
}
