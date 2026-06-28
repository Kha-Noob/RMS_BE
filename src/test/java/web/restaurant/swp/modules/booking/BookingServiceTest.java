package web.restaurant.swp.modules.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import web.restaurant.swp.modules.booking.model.Booking;
import web.restaurant.swp.modules.booking.repository.BookingRepository;
import web.restaurant.swp.modules.booking.service.BookingService;
import web.restaurant.swp.modules.pos.repository.TableRepository;
import web.restaurant.swp.modules.pos.model.TableEntity;
import web.restaurant.swp.modules.pos.model.Room;
import web.restaurant.swp.modules.branch.model.Branch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TableRepository tableRepository;

    @InjectMocks
    private BookingService bookingService;

    private Booking existingBooking;

    @BeforeEach
    void setUp() {
        existingBooking = Booking.builder()
                .id(1L)
                .customerName("John Doe")
                .customerPhone("0912345678")
                .bookingTime(LocalDateTime.now().plusDays(2)) // 2 days in future
                .guests(4)
                .branchId("01-2thang9")
                .tableId(5L)
                .tableLabel("Table 5")
                .depositAmount(100000.0)
                .paymentStatus("PAID")
                .status("CONFIRMED")
                .build();
    }

    @Test
    void createBooking_ShouldThrowException_WhenTableIsAlreadyBooked() {
        // Arrange
        Booking newBooking = Booking.builder()
                .customerName("Jane Doe")
                .customerPhone("0987654321")
                .guests(2)
                .branchId("01-2thang9")
                .bookingTime(LocalDateTime.now().plusDays(2))
                .tableId(5L)
                .build();

        Branch branch = Branch.builder().branchId("01-2thang9").build();
        Room room = Room.builder().branch(branch).build();
        TableEntity table = TableEntity.builder()
                .id(5L)
                .name("Table 5")
                .capacity(4)
                .room(room)
                .build();

        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));

        // Simulate active booking at the same table
        List<Booking> activeBookings = new ArrayList<>();
        activeBookings.add(existingBooking);
        when(bookingRepository.findByBranchIdAndStatusNotInAndBookingTimeBetween(
                eq("01-2thang9"), any(java.util.Collection.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(activeBookings);

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            bookingService.createBooking(newBooking);
        });

        assertTrue(exception.getMessage().contains("Bàn này đã được đặt trước"));
    }

    @Test
    void cancelBooking_ShouldRefund100Percent_WhenMoreThan24Hours() {
        // Arrange
        existingBooking.setBookingTime(LocalDateTime.now().plusHours(30)); // 30 hours in future
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(existingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Booking result = bookingService.cancelBooking(1L);

        // Assert
        assertEquals("CANCELLED", result.getStatus());
        assertEquals("REFUNDED", result.getPaymentStatus());
        assertTrue(result.getNotes().contains("hoàn cọc 100%"));
    }

    @Test
    void cancelBooking_ShouldRefund50Percent_WhenBetween2And24Hours() {
        // Arrange
        existingBooking.setBookingTime(LocalDateTime.now().plusHours(5)); // 5 hours in future
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(existingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Booking result = bookingService.cancelBooking(1L);

        // Assert
        assertEquals("CANCELLED", result.getStatus());
        assertEquals("PARTIALLY_REFUNDED", result.getPaymentStatus());
        assertTrue(result.getNotes().contains("hoàn cọc 50%"));
    }

    @Test
    void cancelBooking_ShouldThrowException_WhenLessThan2Hours() {
        // Arrange
        existingBooking.setBookingTime(LocalDateTime.now().plusMinutes(90)); // 90 minutes in future
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(existingBooking));

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            bookingService.cancelBooking(1L);
        });

        assertTrue(exception.getMessage().contains("Không thể hủy đặt bàn trước giờ hẹn dưới 2 tiếng"));
    }

    @Test
    void createBooking_ShouldThrowException_WhenBookingTimeIsInPast() {
        Booking booking = Booking.builder()
                .customerName("Jane Doe")
                .customerPhone("0912345678")
                .bookingTime(LocalDateTime.now().minusHours(1)) // in the past
                .guests(4)
                .branchId("01-2thang9")
                .build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            bookingService.createBooking(booking);
        });

        assertTrue(exception.getMessage().contains("Thời gian đặt bàn phải ở tương lai"));
    }

    @Test
    void createBooking_ShouldThrowException_WhenGuestsExceedTableCapacity() {
        Booking booking = Booking.builder()
                .customerName("Jane Doe")
                .customerPhone("0912345678")
                .bookingTime(LocalDateTime.now().plusDays(1))
                .guests(5) // guests = 5
                .branchId("01-2thang9")
                .tableId(5L)
                .build();

        Branch branch = Branch.builder().branchId("01-2thang9").build();
        Room room = Room.builder().branch(branch).build();
        TableEntity table = TableEntity.builder()
                .id(5L)
                .name("Table 5")
                .capacity(4) // capacity = 4
                .room(room)
                .build();

        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            bookingService.createBooking(booking);
        });

        assertTrue(exception.getMessage().contains("Sức chứa của bàn không đủ"));
    }

    @Test
    void createBooking_ShouldThrowException_WhenDoubleBookingBySameCustomer() {
        Booking booking = Booking.builder()
                .customerName("Jane Doe")
                .customerPhone("0912345678")
                .bookingTime(existingBooking.getBookingTime().plusMinutes(30)) // overlaps with existingBooking
                .guests(2)
                .branchId("01-2thang9")
                .build();

        List<Booking> activeBookings = new ArrayList<>();
        activeBookings.add(existingBooking);

        when(bookingRepository.findByCustomerPhoneAndStatusInAndBookingTimeBetween(
                eq("0912345678"), any(java.util.Collection.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(activeBookings);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            bookingService.createBooking(booking);
        });

        assertTrue(exception.getMessage().contains("Khách hàng đã có lượt đặt bàn khác đang hoạt động"));
    }

    @Test
    void createBooking_ShouldThrowException_WhenIntervalOverlapsWithDifferentDurations() {
        // Arrange: Existing booking at 10:00 AM (duration 120m + 15m buffer -> ends 12:15)
        LocalDateTime existingTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        existingBooking.setBookingTime(existingTime);
        existingBooking.setDurationMinutes(120);

        // New booking at 11:00 AM (overlaps since 11:00 is before 12:15)
        LocalDateTime newTime = existingTime.plusHours(1);
        Booking newBooking = Booking.builder()
                .customerName("Jane Doe")
                .customerPhone("0987654321")
                .guests(2)
                .branchId("01-2thang9")
                .bookingTime(newTime)
                .tableId(5L)
                .durationMinutes(120)
                .build();

        Branch branch = Branch.builder().branchId("01-2thang9").build();
        Room room = Room.builder().branch(branch).build();
        TableEntity table = TableEntity.builder()
                .id(5L)
                .name("Table 5")
                .capacity(4)
                .room(room)
                .build();

        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));

        List<Booking> activeBookings = new ArrayList<>();
        activeBookings.add(existingBooking);
        when(bookingRepository.findByBranchIdAndStatusNotInAndBookingTimeBetween(
                eq("01-2thang9"), any(java.util.Collection.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(activeBookings);

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            bookingService.createBooking(newBooking);
        });

        assertTrue(exception.getMessage().contains("Bàn này đã được đặt trước"));
    }

    @Test
    void createBooking_ShouldSucceed_WhenBookingTimeIsJustAfterPreviousDurationAndBuffer() {
        // Arrange: Existing booking at 10:00 AM (duration 120m + 15m buffer -> ends 12:15)
        LocalDateTime existingTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        existingBooking.setBookingTime(existingTime);
        existingBooking.setDurationMinutes(120);

        // New booking at 12:15 PM (allowed since 12:15 is exactly end of previous block)
        LocalDateTime newTime = existingTime.plusMinutes(135);
        Booking newBooking = Booking.builder()
                .customerName("Jane Doe")
                .customerPhone("0987654321")
                .guests(2)
                .branchId("01-2thang9")
                .bookingTime(newTime)
                .tableId(5L)
                .durationMinutes(120)
                .build();

        Branch branch = Branch.builder().branchId("01-2thang9").build();
        Room room = Room.builder().branch(branch).build();
        TableEntity table = TableEntity.builder()
                .id(5L)
                .name("Table 5")
                .capacity(4)
                .room(room)
                .build();

        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));

        List<Booking> activeBookings = new ArrayList<>();
        activeBookings.add(existingBooking);
        when(bookingRepository.findByBranchIdAndStatusNotInAndBookingTimeBetween(
                eq("01-2thang9"), any(java.util.Collection.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(activeBookings);

        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Booking result = bookingService.createBooking(newBooking);

        // Assert
        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
    }
}
