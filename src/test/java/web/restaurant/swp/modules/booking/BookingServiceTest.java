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
                .branchId("01-2thang9")
                .bookingTime(LocalDateTime.now().plusDays(2))
                .tableId(5L)
                .build();

        // Simulate active booking at the same table
        List<Booking> activeBookings = new ArrayList<>();
        activeBookings.add(existingBooking);
        when(bookingRepository.findByBranchIdAndStatusNotAndBookingTimeBetween(
                eq("01-2thang9"), eq("CANCELLED"), any(LocalDateTime.class), any(LocalDateTime.class)))
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
}
