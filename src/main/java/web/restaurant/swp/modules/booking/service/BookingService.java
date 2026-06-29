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

    private static final int CLEANING_BUFFER_MINUTES = 15;

    private boolean isOverlapping(LocalDateTime startA, int durationA, LocalDateTime startB, int durationB) {
        LocalDateTime endA = startA.plusMinutes(durationA + CLEANING_BUFFER_MINUTES);
        LocalDateTime endB = startB.plusMinutes(durationB + CLEANING_BUFFER_MINUTES);
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    /**
     * Get the list of table IDs that are already booked for a specific branch and time slot.
     * Checks for overlap assuming a default 120-minute reservation.
     */
    public List<Long> getBookedTableIds(String branchId, LocalDateTime bookingTime) {
        return getBookedTableIds(branchId, bookingTime, 120);
    }

    /**
     * Get the list of table IDs that are already booked for a specific branch and time slot with custom duration.
     */
    public List<Long> getBookedTableIds(String branchId, LocalDateTime bookingTime, int newDurationMinutes) {
        // Query active bookings within a 4-hour window on either side to minimize DB load
        LocalDateTime queryStart = bookingTime.minusHours(4);
        LocalDateTime queryEnd = bookingTime.plusHours(4);
        
        List<Booking> activeBookings = bookingRepository.findByBranchIdAndStatusNotInAndBookingTimeBetween(
                branchId, List.of("CANCELLED", "NO_SHOW"), queryStart, queryEnd);
                
        return activeBookings.stream()
                .filter(b -> isOverlapping(b.getBookingTime(), b.getDurationMinutes() != null ? b.getDurationMinutes() : 120, bookingTime, newDurationMinutes))
                .map(Booking::getTableId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Create a new booking with validations.
     */
    @Transactional
    public Booking createBooking(Booking booking) {
        // 1. Basic Metadata Validation
        if (booking.getCustomerName() == null || booking.getCustomerName().trim().isEmpty()) {
            throw new RuntimeException("Tên khách hàng không được để trống!");
        }
        if (booking.getCustomerPhone() == null || booking.getCustomerPhone().trim().isEmpty()) {
            throw new RuntimeException("Số điện thoại không được để trống!");
        }
        // Validate phone number format (between 9 to 15 characters, containing numbers/spaces/hyphens/parentheses)
        if (!booking.getCustomerPhone().matches("^[0-9\\+\\-\\s()]{9,15}$")) {
            throw new RuntimeException("Số điện thoại không đúng định dạng!");
        }
        if (booking.getGuests() == null || booking.getGuests() <= 0) {
            throw new RuntimeException("Số lượng khách phải lớn hơn 0!");
        }
        if (booking.getBranchId() == null || booking.getBranchId().trim().isEmpty()) {
            throw new RuntimeException("Mã chi nhánh không được để trống!");
        }
        if (booking.getBookingTime() == null) {
            throw new RuntimeException("Thời gian đặt bàn không được để trống!");
        }

        // 2. Booking Time restriction validation
        if (booking.getBookingTime().isBefore(LocalDateTime.now().plusMinutes(15))) {
            throw new RuntimeException("Thời gian đặt bàn phải ở tương lai (tối thiểu trước 15 phút)!");
        }
        if (booking.getBookingTime().isAfter(LocalDateTime.now().plusDays(30))) {
            throw new RuntimeException("Chỉ được đặt bàn trước tối đa 30 ngày!");
        }

        if (booking.getDurationMinutes() == null || booking.getDurationMinutes() <= 0) {
            booking.setDurationMinutes(120);
        }

        // 3. Double booking validation for the same customer using interval overlap
        LocalDateTime queryStart = booking.getBookingTime().minusHours(4);
        LocalDateTime queryEnd = booking.getBookingTime().plusHours(4);
        List<Booking> customerBookings = bookingRepository.findByCustomerPhoneAndStatusInAndBookingTimeBetween(
                booking.getCustomerPhone(), List.of("PENDING", "CONFIRMED", "CHECKED_IN"), queryStart, queryEnd);
        
        int newDuration = booking.getDurationMinutes();
        boolean customerOverlap = customerBookings.stream()
                .anyMatch(b -> isOverlapping(b.getBookingTime(), b.getDurationMinutes() != null ? b.getDurationMinutes() : 120, booking.getBookingTime(), newDuration));
        
        if (customerOverlap) {
            throw new RuntimeException("Khách hàng đã có lượt đặt bàn khác đang hoạt động trong khoảng thời gian này!");
        }

        // 4. Table selection & capacity check
        if (booking.getTableId() != null) {
            web.restaurant.swp.modules.pos.model.TableEntity table = tableRepository.findById(booking.getTableId())
                    .orElseThrow(() -> new RuntimeException("Bàn được chọn không tồn tại!"));

            // Validate table belongs to the branch
            if (table.getRoom() == null || table.getRoom().getBranch() == null || 
                !table.getRoom().getBranch().getBranchId().equals(booking.getBranchId())) {
                throw new RuntimeException("Bàn được chọn không thuộc chi nhánh này!");
            }

            // Validate capacity
            if (table.getCapacity() < booking.getGuests()) {
                throw new RuntimeException("Sức chứa của bàn không đủ cho số lượng khách!");
            }

            // Validate duplicate table booking
            List<Long> bookedIds = getBookedTableIds(booking.getBranchId(), booking.getBookingTime(), newDuration);
            if (bookedIds.contains(booking.getTableId())) {
                throw new RuntimeException("Bàn này đã được đặt trước hoặc đang được sử dụng trong khoảng thời gian này!");
            }
            
            // Set table label from DB if not provided
            if (booking.getTableLabel() == null || booking.getTableLabel().trim().isEmpty()) {
                booking.setTableLabel(table.getName());
            }
        }

        // 5. Event capacity validation if booking is for an event
        if (booking.getTableId() == null && booking.getNotes() != null && booking.getNotes().contains("sự kiện")) {
            String eventTitle = "";
            int colonIndex = booking.getNotes().indexOf(":");
            if (colonIndex != -1) {
                eventTitle = booking.getNotes().substring(colonIndex + 1).trim();
            } else {
                eventTitle = booking.getNotes().trim();
            }

            int maxCapacity = getEventMaxCapacity(eventTitle);
            if (maxCapacity < Integer.MAX_VALUE) {
                java.time.LocalDate localDate = booking.getBookingTime().toLocalDate();
                java.time.LocalDateTime start = localDate.atStartOfDay();
                java.time.LocalDateTime end = localDate.atTime(java.time.LocalTime.MAX);
                
                List<Booking> bookings = bookingRepository.findByBranchIdAndBookingTimeBetween(booking.getBranchId(), start, end);
                
                final String finalTitle = eventTitle;
                int currentBooked = bookings.stream()
                        .filter(b -> b.getNotes() != null && b.getNotes().toLowerCase().contains(finalTitle.toLowerCase()))
                        .filter(b -> !"CANCELLED".equals(b.getStatus()) && !"NO_SHOW".equals(b.getStatus()))
                        .mapToInt(Booking::getGuests)
                        .sum();

                if (currentBooked + booking.getGuests() > maxCapacity) {
                    throw new RuntimeException("Sự kiện này đã hết chỗ cho ngày " + 
                            localDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + 
                            "! (Chỉ còn lại " + (maxCapacity - currentBooked) + " chỗ)");
                }
            }
        }

        booking.setCreatedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        if (booking.getStatus() == null || booking.getStatus().trim().isEmpty()) {
            booking.setStatus("PENDING");
        }

        if (booking.getDepositPaid() == null) {
            booking.setDepositPaid(false);
        }

        if (booking.getSource() == null || booking.getSource().trim().isEmpty()) {
            booking.setSource("ONLINE");
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

        LocalDateTime newTime = updated.getBookingTime() != null ? updated.getBookingTime() : existing.getBookingTime();
        Long newTableId = updated.getTableId() != null ? updated.getTableId() : existing.getTableId();
        String branchId = updated.getBranchId() != null ? updated.getBranchId() : existing.getBranchId();
        Integer guests = updated.getGuests() != null ? updated.getGuests() : existing.getGuests();
        int currentNewDuration = updated.getDurationMinutes() != null ? updated.getDurationMinutes() : (existing.getDurationMinutes() != null ? existing.getDurationMinutes() : 120);

        // Validate new time if changed
        if (updated.getBookingTime() != null && !updated.getBookingTime().isEqual(existing.getBookingTime())) {
            if (updated.getBookingTime().isBefore(LocalDateTime.now().plusMinutes(15))) {
                throw new RuntimeException("Thời gian đặt bàn mới phải ở tương lai (tối thiểu trước 15 phút)!");
            }
            if (updated.getBookingTime().isAfter(LocalDateTime.now().plusDays(30))) {
                throw new RuntimeException("Chỉ được đặt bàn trước tối đa 30 ngày!");
            }
        }

        // Validate table capacity & branch match if table/guests/time changes
        if (newTableId != null && (updated.getTableId() != null || updated.getGuests() != null || updated.getBookingTime() != null)) {
            web.restaurant.swp.modules.pos.model.TableEntity table = tableRepository.findById(newTableId)
                    .orElseThrow(() -> new RuntimeException("Bàn được chọn không tồn tại!"));

            if (table.getRoom() == null || table.getRoom().getBranch() == null || 
                !table.getRoom().getBranch().getBranchId().equals(branchId)) {
                throw new RuntimeException("Bàn được chọn không thuộc chi nhánh này!");
            }

            if (table.getCapacity() < guests) {
                throw new RuntimeException("Sức chứa của bàn không đủ cho số lượng khách!");
            }
        }

        // Validate table conflict if table, time, or duration changed
        if (newTableId != null && (updated.getTableId() != null || updated.getBookingTime() != null || updated.getDurationMinutes() != null)) {
            LocalDateTime start = newTime.minusHours(4);
            LocalDateTime end = newTime.plusHours(4);
            
            List<Booking> conflicting = bookingRepository.findByBranchIdAndStatusNotInAndBookingTimeBetween(
                    branchId, List.of("CANCELLED", "NO_SHOW"), start, end);
            
            boolean conflict = conflicting.stream()
                    .filter(b -> !b.getId().equals(existing.getId()))
                    .filter(b -> b.getTableId() != null && b.getTableId().equals(newTableId))
                    .anyMatch(b -> isOverlapping(b.getBookingTime(), b.getDurationMinutes() != null ? b.getDurationMinutes() : 120, newTime, currentNewDuration));
                    
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

    private int getEventMaxCapacity(String eventTitle) {
        if (eventTitle == null) return Integer.MAX_VALUE;
        String title = eventTitle.toLowerCase();
        if (title.contains("đêm nhạc acoustic")) return 40;
        if (title.contains("sushi")) return 15;
        if (title.contains("bia thủ công") || title.contains("bbq")) return 300;
        if (title.contains("truffle")) return 20;
        if (title.contains("cocktail") || title.contains("pha chế")) return 25;
        return Integer.MAX_VALUE;
    }
}
