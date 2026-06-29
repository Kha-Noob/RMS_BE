package web.restaurant.swp.modules.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.branch.model.Branch;
import web.restaurant.swp.modules.branch.repository.BranchRepository;
import web.restaurant.swp.modules.branch.service.BranchAccessService;
import web.restaurant.swp.modules.booking.model.Booking;
import web.restaurant.swp.modules.booking.repository.BookingRepository;
import web.restaurant.swp.modules.event.model.Event;
import web.restaurant.swp.modules.event.repository.EventRepository;
import web.restaurant.swp.modules.pos.model.Order;
import web.restaurant.swp.modules.pos.repository.OrderRepository;
import web.restaurant.swp.modules.review.model.CustomerReview;
import web.restaurant.swp.modules.review.repository.CustomerReviewRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final BranchAccessService branchAccessService;
    private final BranchRepository branchRepository;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final CustomerReviewRepository customerReviewRepository;
    private final EventRepository eventRepository;

    @GetMapping("/api/dashboard/summary")
    public ResponseEntity<?> getDashboardSummary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            LocalDate today = LocalDate.now();
            LocalDate rangeStartDate;
            LocalDate rangeEndDate;

            if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
                rangeStartDate = LocalDate.parse(startDate);
                rangeEndDate = LocalDate.parse(endDate);
                if (rangeStartDate.isAfter(rangeEndDate)) {
                    return ResponseEntity.badRequest().body("startDate must not be after endDate");
                }
            } else {
                rangeStartDate = today;
                rangeEndDate = today;
            }

            LocalDateTime rangeStartDateTime = rangeStartDate.atStartOfDay();
            LocalDateTime rangeEndDateTime = rangeEndDate.plusDays(1).atStartOfDay();

            Branch branch = branchRepository.findById(branchId).orElse(null);
            String branchName = branch != null ? branch.getName() : "";

            // Orders in range
            List<Order> allBranchOrders = orderRepository.findByBranchId(branchId);
            List<Order> rangeOrders = allBranchOrders.stream()
                    .filter(o -> o.getOrderDate() != null && !o.getOrderDate().isBefore(rangeStartDateTime) && o.getOrderDate().isBefore(rangeEndDateTime))
                    .filter(o -> "SERVED".equalsIgnoreCase(o.getStatus()))
                    .collect(Collectors.toList());

            // Bookings in range
            List<Booking> rangeBookings = bookingRepository.findByBranchIdAndBookingTimeBetween(branchId, rangeStartDateTime, rangeEndDateTime);

            // Reviews in range
            List<CustomerReview> rangeReviews = customerReviewRepository.findByBranchIdAndCreatedAtBetween(branchId, rangeStartDateTime, rangeEndDateTime);

            // ── KPI Calculations ──
            long customerVisits = rangeOrders.size();

            double totalRevenue = rangeOrders.stream()
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                    .sum();

            // Booking success: CHECKED_IN means customer came and dined
            long totalBookings = rangeBookings.size();
            long successfulBookings = rangeBookings.stream()
                    .filter(b -> "CHECKED_IN".equalsIgnoreCase(b.getStatus()))
                    .count();
            double bookingSuccessRate = totalBookings > 0
                    ? Math.round((double) successfulBookings / totalBookings * 100.0 * 10.0) / 10.0
                    : 0.0;

            long onlineBookingCount = rangeBookings.stream()
                    .filter(b -> "ONLINE".equalsIgnoreCase(b.getSource()))
                    .count();
            long offlineBookingCount = rangeBookings.stream()
                    .filter(b -> "OFFLINE".equalsIgnoreCase(b.getSource()))
                    .count();

            long onlineOrderCount = rangeOrders.stream()
                    .filter(o -> "ONLINE".equalsIgnoreCase(o.getSource()))
                    .count();
            long offlineOrderCount = rangeOrders.stream()
                    .filter(o -> "OFFLINE".equalsIgnoreCase(o.getSource()))
                    .count();

            double averageCustomerRating = rangeReviews.isEmpty()
                    ? 0.0
                    : Math.round(rangeReviews.stream()
                            .mapToInt(CustomerReview::getRating)
                            .average()
                            .orElse(0.0) * 10.0) / 10.0;

            // Revenue trend
            List<Map<String, Object>> revenueTrend = buildRevenueTrend(rangeStartDate, rangeEndDate, allBranchOrders);

            // Build response
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("branchId", branchId);
            result.put("branchName", branchName);
            result.put("startDate", rangeStartDate.toString());
            result.put("endDate", rangeEndDate.toString());
            result.put("customerVisits", customerVisits);
            result.put("totalRevenue", totalRevenue);
            result.put("bookingSuccessRate", bookingSuccessRate);
            result.put("onlineBookingCount", onlineBookingCount);
            result.put("offlineBookingCount", offlineBookingCount);
            result.put("onlineOrderCount", onlineOrderCount);
            result.put("offlineOrderCount", offlineOrderCount);
            result.put("averageCustomerRating", averageCustomerRating);
            result.put("revenueTrend", revenueTrend);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private List<Map<String, Object>> buildRevenueTrend(LocalDate rangeStartDate, LocalDate rangeEndDate, List<Order> orders) {
        boolean singleDay = rangeStartDate.equals(rangeEndDate);

        if (singleDay) {
            DateTimeFormatter hourFmt = DateTimeFormatter.ofPattern("HH:00");
            Map<String, Double> hourlyRevenue = new LinkedHashMap<>();
            for (int h = 0; h <= 23; h++) {
                hourlyRevenue.put(String.format("%02d:00", h), 0.0);
            }
            for (Order o : orders) {
                if (o.getOrderDate() != null && o.getOrderDate().toLocalDate().equals(rangeStartDate)) {
                    String key = o.getOrderDate().format(hourFmt);
                    hourlyRevenue.merge(key, o.getTotalAmount() != null ? o.getTotalAmount() : 0.0, Double::sum);
                }
            }
            List<Map<String, Object>> trend = new ArrayList<>();
            for (Map.Entry<String, Double> entry : hourlyRevenue.entrySet()) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("date", entry.getKey());
                point.put("revenue", Math.round(entry.getValue()));
                trend.add(point);
            }
            return trend;
        } else {
            DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd/MM");
            Map<String, Double> dailyRevenue = new LinkedHashMap<>();
            for (LocalDate d = rangeStartDate; !d.isAfter(rangeEndDate); d = d.plusDays(1)) {
                dailyRevenue.put(d.format(dayFmt), 0.0);
            }
            for (Order o : orders) {
                if (o.getOrderDate() != null) {
                    LocalDate orderDate = o.getOrderDate().toLocalDate();
                    if (!orderDate.isBefore(rangeStartDate) && !orderDate.isAfter(rangeEndDate)) {
                        String key = orderDate.format(dayFmt);
                        dailyRevenue.merge(key, o.getTotalAmount() != null ? o.getTotalAmount() : 0.0, Double::sum);
                    }
                }
            }
            List<Map<String, Object>> trend = new ArrayList<>();
            for (Map.Entry<String, Double> entry : dailyRevenue.entrySet()) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("date", entry.getKey());
                point.put("revenue", Math.round(entry.getValue()));
                trend.add(point);
            }
            return trend;
        }
    }

    private double parseTicketPrice(String priceStr) {
        if (priceStr == null) return 0.0;
        String clean = priceStr.toLowerCase();
        if (clean.contains("miễn phí") || clean.contains("free") || clean.contains("free admission")) {
            return 0.0;
        }
        // Keep only digits
        clean = clean.replaceAll("[^0-9]", "");
        if (clean.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0.0;
        }
    }

    @GetMapping("/api/dashboard/admin/commissions")
    public ResponseEntity<?> getAdminCommissions() {
        List<Event> events = eventRepository.findAll();
        List<Booking> allBookings = bookingRepository.findAll();

        double grandTotalCommission = 0.0;
        double grandTotalRevenue = 0.0;
        int grandTotalTickets = 0;
        int activeEventsCount = 0;
        int expiredEventsCount = 0;

        List<Map<String, Object>> ledger = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Event event : events) {
            double ticketPrice = parseTicketPrice(event.getPrice());

            List<Booking> eventBookings = allBookings.stream()
                    .filter(b -> {
                        if (b.getEventId() != null) {
                            return b.getEventId().equals(event.getId());
                        }
                        return b.getNotes() != null && b.getNotes().toLowerCase().contains(event.getTitle().toLowerCase());
                    })
                    .filter(b -> !"CANCELLED".equals(b.getStatus()) && !"NO_SHOW".equals(b.getStatus()))
                    .filter(b -> "PAID".equals(b.getPaymentStatus()) || (b.getDepositPaid() != null && b.getDepositPaid()))
                    .collect(Collectors.toList());

            int totalTickets = eventBookings.stream().mapToInt(Booking::getGuests).sum();
            double totalRevenue = totalTickets * ticketPrice;
            double commissionRate = event.getCommissionRate() != null ? event.getCommissionRate() : 10.0;
            double commissionAmount = totalRevenue * (commissionRate / 100.0);

            boolean isExpired = event.getBookingDeadline() != null && now.isAfter(event.getBookingDeadline());

            if (isExpired) {
                expiredEventsCount++;
            } else {
                activeEventsCount++;
            }

            grandTotalCommission += commissionAmount;
            grandTotalRevenue += totalRevenue;
            grandTotalTickets += totalTickets;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", event.getId());
            item.put("title", event.getTitle());
            item.put("restaurantName", event.getRestaurantName());
            item.put("isExpired", isExpired);
            item.put("bookingDeadline", event.getBookingDeadline());
            item.put("ticketPrice", ticketPrice);
            item.put("totalTickets", totalTickets);
            item.put("totalRevenue", totalRevenue);
            item.put("commissionRate", commissionRate);
            item.put("commissionAmount", commissionAmount);

            ledger.add(item);
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalCommission", grandTotalCommission);
        kpis.put("totalRevenue", grandTotalRevenue);
        kpis.put("totalTickets", grandTotalTickets);
        kpis.put("activeEventsCount", activeEventsCount);
        kpis.put("expiredEventsCount", expiredEventsCount);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("kpis", kpis);
        response.put("ledger", ledger);

        return ResponseEntity.ok(response);
    }
}
