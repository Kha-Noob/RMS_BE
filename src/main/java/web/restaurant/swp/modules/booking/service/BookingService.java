package web.restaurant.swp.modules.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.restaurant.swp.modules.booking.model.Booking;
import web.restaurant.swp.modules.booking.repository.BookingRepository;
import web.restaurant.swp.modules.pos.repository.TableRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TableRepository tableRepository;

    /**
     * Get the list of table IDs that are already booked for a specific branch and time slot.
     * We check for active bookings (status != CANCELLED) within a 2-hour window.
     */
    public List<Long> getBookedTableIds(String branchId, LocalDateTime bookingTime) {
        LocalDateTime start = bookingTime.minusHours(2);
        LocalDateTime end = bookingTime.plusHours(2);
        
        List<Booking> activeBookings = bookingRepository.findByBranchIdAndStatusNotAndBookingTimeBetween(
                branchId, "CANCELLED", start, end);
                
        return activeBookings.stream()
                .map(Booking::getTableId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Create a new booking with validations.
     */
    @Transactional
    public Booking createBooking(Booking booking) {
        booking.setCreatedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        if (booking.getStatus() == null || booking.getStatus().trim().isEmpty()) {
            booking.setStatus("PENDING");
        }

        // Validate table selection if specified
        if (booking.getTableId() != null) {
            List<Long> bookedIds = getBookedTableIds(booking.getBranchId(), booking.getBookingTime());
            if (bookedIds.contains(booking.getTableId())) {
                throw new RuntimeException("Bàn này đã được đặt trước hoặc đang được sử dụng trong khoảng thời gian này!");
            }
        }

        // Calculate deposit if they pre-ordered items or selected VIP/holiday
        if (booking.getOrderedItemsJson() != null && !booking.getOrderedItemsJson().trim().isEmpty()) {
            // If they ordered in advance, require a deposit
            if (booking.getDepositAmount() == null || booking.getDepositAmount() <= 0) {
                booking.setDepositAmount(100000.0); // Default flat deposit
            }
        }

        return bookingRepository.save(booking);
    }

    /**
     * Update an existing booking with validations.
     */
    @Transactional
    public Booking updateBooking(Long id, Booking updated) {
        Booking existing = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lượt đặt bàn #" + id));

        // Enforce 2-hour adjustment limit (US#7)
        if (LocalDateTime.now().isAfter(existing.getBookingTime().minusHours(2))) {
            throw new RuntimeException("Không thể chỉnh sửa lượt đặt bàn trước giờ hẹn dưới 2 tiếng!");
        }

        // Validate table conflict if the time or table has changed
        if (updated.getTableId() != null && 
            (!updated.getTableId().equals(existing.getTableId()) || !updated.getBookingTime().isEqual(existing.getBookingTime()))) {
            
            LocalDateTime start = updated.getBookingTime().minusHours(2);
            LocalDateTime end = updated.getBookingTime().plusHours(2);
            
            List<Booking> conflicting = bookingRepository.findByBranchIdAndStatusNotAndBookingTimeBetween(
                    updated.getBranchId() != null ? updated.getBranchId() : existing.getBranchId(), 
                    "CANCELLED", start, end);
            
            boolean conflict = conflicting.stream()
                    .filter(b -> !b.getId().equals(existing.getId()))
                    .map(Booking::getTableId)
                    .filter(Objects::nonNull)
                    .anyMatch(tid -> tid.equals(updated.getTableId()));
                    
            if (conflict) {
                throw new RuntimeException("Bàn này đã được đặt trong khoảng thời gian mới được chọn!");
            }
        }

        // Update fields
        if (updated.getCustomerName() != null) existing.setCustomerName(updated.getCustomerName());
        if (updated.getCustomerPhone() != null) existing.setCustomerPhone(updated.getCustomerPhone());
        if (updated.getCustomerEmail() != null) existing.setCustomerEmail(updated.getCustomerEmail());
        if (updated.getBookingTime() != null) existing.setBookingTime(updated.getBookingTime());
        if (updated.getGuests() != null) existing.setGuests(updated.getGuests());
        if (updated.getNotes() != null) existing.setNotes(updated.getNotes());
        if (updated.getTableId() != null) existing.setTableId(updated.getTableId());
        if (updated.getTableLabel() != null) existing.setTableLabel(updated.getTableLabel());
        if (updated.getDietaryNotes() != null) existing.setDietaryNotes(updated.getDietaryNotes());
        
        existing.setAllergyPeanut(updated.getAllergyPeanut());
        existing.setAllergyGluten(updated.getAllergyGluten());
        if (updated.getAllergyOthers() != null) existing.setAllergyOthers(updated.getAllergyOthers());
        if (updated.getOrderedItemsJson() != null) existing.setOrderedItemsJson(updated.getOrderedItemsJson());
        if (updated.getDepositAmount() != null) existing.setDepositAmount(updated.getDepositAmount());
        if (updated.getPaymentMethod() != null) existing.setPaymentMethod(updated.getPaymentMethod());
        if (updated.getPaymentStatus() != null) existing.setPaymentStatus(updated.getPaymentStatus());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());
        
        existing.setUpdatedAt(LocalDateTime.now());
        return bookingRepository.save(existing);
    }

    /**
     * Cancel an existing booking with refund calculation (US#5 & US#7).
     */
    @Transactional
    public Booking cancelBooking(Long id) {
        Booking existing = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lượt đặt bàn #" + id));

        // Enforce 2-hour cancellation limit (US#7)
        if (LocalDateTime.now().isAfter(existing.getBookingTime().minusHours(2))) {
            throw new RuntimeException("Không thể hủy đặt bàn trước giờ hẹn dưới 2 tiếng!");
        }

        existing.setStatus("CANCELLED");
        existing.setUpdatedAt(LocalDateTime.now());

        // Process refund calculations based on time before appointment (US#5)
        if (existing.getDepositAmount() != null && existing.getDepositAmount() > 0 && "PAID".equalsIgnoreCase(existing.getPaymentStatus())) {
            long hoursBefore = Duration.between(LocalDateTime.now(), existing.getBookingTime()).toHours();
            
            if (hoursBefore >= 24) {
                // 100% refund
                existing.setPaymentStatus("REFUNDED");
                existing.setNotes((existing.getNotes() != null ? existing.getNotes() : "") + " [Đã hoàn cọc 100% (hủy trước 24h)]");
            } else if (hoursBefore >= 2) {
                // 50% refund
                existing.setPaymentStatus("PARTIALLY_REFUNDED");
                double refundAmt = existing.getDepositAmount() * 0.5;
                existing.setNotes((existing.getNotes() != null ? existing.getNotes() : "") + " [Đã hoàn cọc 50%: " + refundAmt + "đ (hủy trước 2h)]");
            } else {
                // Under 2 hours (should already be blocked by condition above, but here as fallback)
                existing.setPaymentStatus("NO_REFUND");
                existing.setNotes((existing.getNotes() != null ? existing.getNotes() : "") + " [Không hoàn cọc (hủy trễ)]");
            }
        }

        return bookingRepository.save(existing);
    }
}
