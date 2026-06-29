package web.restaurant.swp.modules.event.controller;

import web.restaurant.swp.modules.event.model.Event;
import web.restaurant.swp.modules.event.repository.EventRepository;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.booking.model.Booking;
import web.restaurant.swp.modules.booking.repository.BookingRepository;
import web.restaurant.swp.modules.tenant.model.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class EventController {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    private String getLoggedInUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Map<String, Object> toEventDto(Event e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", e.getId());
        map.put("title", e.getTitle());
        map.put("date", e.getDate());
        map.put("time", e.getTime());
        map.put("location", e.getLocation());
        map.put("restaurantName", e.getRestaurantName());
        map.put("tag", e.getTag());
        map.put("imageUrl", e.getImageUrl());
        map.put("price", e.getPrice());
        map.put("capacity", e.getCapacity());
        map.put("description", e.getDescription());
        map.put("highlights", e.getHighlights() != null && !e.getHighlights().trim().isEmpty() 
            ? Arrays.asList(e.getHighlights().split(";")) 
            : Collections.emptyList());
        map.put("branchId", e.getBranchId());
        map.put("eventDates", e.getEventDates() != null && !e.getEventDates().trim().isEmpty() 
            ? Arrays.asList(e.getEventDates().split(",")) 
            : Collections.emptyList());
        map.put("status", e.getStatus());
        map.put("commissionRate", e.getCommissionRate());
        map.put("isUsingSystemWeb", e.getIsUsingSystemWeb());
        map.put("bookingDeadline", e.getBookingDeadline());
        map.put("createdBy", e.getCreatedBy());

        // Attach bank account details of the event creator's tenant
        if (e.getCreatedBy() != null) {
            Optional<User> creatorOpt = userRepository.findByEmail(e.getCreatedBy());
            if (creatorOpt.isPresent()) {
                Tenant tenant = creatorOpt.get().getTenant();
                if (tenant != null) {
                    map.put("bankName", tenant.getBankName() != null ? tenant.getBankName() : "");
                    map.put("bankAccountNo", tenant.getBankAccountNo() != null ? tenant.getBankAccountNo() : "");
                    map.put("bankAccountName", tenant.getBankAccountName() != null ? tenant.getBankAccountName() : "");
                    map.put("bankBranch", tenant.getBankBranch() != null ? tenant.getBankBranch() : "");
                }
            }
        }
        return map;
    }

    @GetMapping("/public")
    public ResponseEntity<?> getPublicEvents() {
        List<Event> list = eventRepository.findByStatusOrderByCreatedAtDesc("APPROVED");
        list = list.stream()
                .filter(e -> e.getBookingDeadline() == null || LocalDateTime.now().isBefore(e.getBookingDeadline()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list.stream().map(this::toEventDto).collect(Collectors.toList()));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<?> getAdminAllEvents() {
        List<Event> list = eventRepository.findAll();
        return ResponseEntity.ok(list.stream().map(this::toEventDto).collect(Collectors.toList()));
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyEvents() {
        String email = getLoggedInUserEmail();
        List<Event> list;
        if (isAdmin()) {
            list = eventRepository.findAll();
        } else {
            list = eventRepository.findByCreatedByOrderByCreatedAtDesc(email);
        }
        return ResponseEntity.ok(list.stream().map(this::toEventDto).collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<?> createEvent(@RequestBody Event event) {
        String email = getLoggedInUserEmail();
        event.setCreatedBy(email);
        event.setCreatedAt(LocalDateTime.now());
        
        if (isAdmin()) {
            event.setStatus("APPROVED");
        } else {
            event.setStatus("PENDING");
        }

        // Auto-calculate system web usage from user's tenant
        boolean usingSystemWeb = false;
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getTenant() != null) {
                usingSystemWeb = user.getTenant().isUsingSystemWeb();
            }
        }
        if (isAdmin()) {
            usingSystemWeb = true;
        }

        event.setIsUsingSystemWeb(usingSystemWeb);
        event.setCommissionRate(usingSystemWeb ? 5.0 : 10.0);

        Event saved = eventRepository.save(event);
        return ResponseEntity.ok(toEventDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(@PathVariable Long id, @RequestBody Event details) {
        Optional<Event> opt = eventRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Event existing = opt.get();
        String email = getLoggedInUserEmail();
        
        if (!isAdmin() && !existing.getCreatedBy().equals(email)) {
            return ResponseEntity.status(403).body("Access Denied");
        }

        existing.setTitle(details.getTitle());
        existing.setDate(details.getDate());
        existing.setTime(details.getTime());
        existing.setLocation(details.getLocation());
        existing.setRestaurantName(details.getRestaurantName());
        existing.setTag(details.getTag());
        existing.setImageUrl(details.getImageUrl());
        existing.setPrice(details.getPrice());
        existing.setCapacity(details.getCapacity());
        existing.setDescription(details.getDescription());
        existing.setHighlights(details.getHighlights());
        existing.setBranchId(details.getBranchId());
        existing.setEventDates(details.getEventDates());
        existing.setBookingDeadline(details.getBookingDeadline());

        // Auto-calculate system web usage from user's tenant
        boolean usingSystemWeb = false;
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getTenant() != null) {
                usingSystemWeb = user.getTenant().isUsingSystemWeb();
            }
        }
        if (isAdmin()) {
            usingSystemWeb = true;
        }

        existing.setIsUsingSystemWeb(usingSystemWeb);
        existing.setCommissionRate(usingSystemWeb ? 5.0 : 10.0);

        if (!isAdmin()) {
            existing.setStatus("PENDING");
        } else if (details.getStatus() != null) {
            existing.setStatus(details.getStatus());
        }

        Event saved = eventRepository.save(existing);
        return ResponseEntity.ok(toEventDto(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(@PathVariable Long id) {
        Optional<Event> opt = eventRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Event existing = opt.get();
        String email = getLoggedInUserEmail();

        if (!isAdmin() && !existing.getCreatedBy().equals(email)) {
            return ResponseEntity.status(403).body("Access Denied");
        }

        eventRepository.delete(existing);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveEvent(@PathVariable Long id) {
        if (!isAdmin()) return ResponseEntity.status(403).body("Access Denied");
        Optional<Event> opt = eventRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Event existing = opt.get();
        existing.setStatus("APPROVED");
        Event saved = eventRepository.save(existing);
        return ResponseEntity.ok(toEventDto(saved));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectEvent(@PathVariable Long id) {
        if (!isAdmin()) return ResponseEntity.status(403).body("Access Denied");
        Optional<Event> opt = eventRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Event existing = opt.get();
        existing.setStatus("REJECTED");
        Event saved = eventRepository.save(existing);
        return ResponseEntity.ok(toEventDto(saved));
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

    @GetMapping("/{id}/billing")
    public ResponseEntity<?> getEventBilling(@PathVariable Long id) {
        Optional<Event> opt = eventRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Event event = opt.get();
        double ticketPrice = parseTicketPrice(event.getPrice());

        // Query bookings
        List<Booking> bookings = bookingRepository.findAll();
        List<Booking> eventBookings = bookings.stream()
                .filter(b -> {
                    if (b.getEventId() != null) {
                        return b.getEventId().equals(event.getId());
                    }
                    // Fallback to title matching
                    return b.getNotes() != null && b.getNotes().toLowerCase().contains(event.getTitle().toLowerCase());
                })
                .filter(b -> !"CANCELLED".equals(b.getStatus()) && !"NO_SHOW".equals(b.getStatus()))
                .filter(b -> "PAID".equals(b.getPaymentStatus()) || (b.getDepositPaid() != null && b.getDepositPaid()))
                .collect(Collectors.toList());

        int totalTickets = eventBookings.stream().mapToInt(Booking::getGuests).sum();
        double totalRevenue = totalTickets * ticketPrice;
        double commissionRate = event.getCommissionRate() != null ? event.getCommissionRate() : 10.0;
        double commissionAmount = totalRevenue * (commissionRate / 100.0);

        boolean isExpired = event.getBookingDeadline() != null && LocalDateTime.now().isAfter(event.getBookingDeadline());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("eventId", event.getId());
        response.put("eventTitle", event.getTitle());
        response.put("bookingDeadline", event.getBookingDeadline());
        response.put("isExpired", isExpired);
        response.put("ticketPrice", ticketPrice);
        response.put("totalTickets", totalTickets);
        response.put("totalRevenue", totalRevenue);
        response.put("commissionRate", commissionRate);
        response.put("commissionAmount", commissionAmount);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cooperator/bookings-summary")
    public ResponseEntity<?> getCooperatorBookingsSummary() {
        String email = getLoggedInUserEmail();
        List<Event> myEvents = eventRepository.findByCreatedByOrderByCreatedAtDesc(email);
        List<Map<String, Object>> summaryList = new ArrayList<>();

        for (Event e : myEvents) {
            List<Booking> eventBookings = bookingRepository.findByEventId(e.getId());
            
            int bookedCount = eventBookings.stream()
                    .filter(b -> !"CANCELLED".equalsIgnoreCase(b.getStatus()))
                    .mapToInt(Booking::getGuests)
                    .sum();

            double totalPaid = eventBookings.stream()
                    .filter(b -> "PAID".equalsIgnoreCase(b.getPaymentStatus()))
                    .mapToDouble(Booking::getDepositAmount)
                    .sum();

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("eventId", e.getId());
            map.put("title", e.getTitle());
            map.put("date", e.getDate());
            map.put("time", e.getTime());
            map.put("price", e.getPrice());
            map.put("capacity", e.getCapacity());
            map.put("status", e.getStatus());
            map.put("bookedCount", bookedCount);
            map.put("totalPaid", totalPaid);
            
            summaryList.add(map);
        }
        return ResponseEntity.ok(summaryList);
    }

    @GetMapping("/cooperator/bookings/{eventId}/details")
    public ResponseEntity<?> getCooperatorBookingDetails(@PathVariable Long eventId) {
        String email = getLoggedInUserEmail();
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Event e = eventOpt.get();
        if (!e.getCreatedBy().equalsIgnoreCase(email) && !isAdmin()) {
            return ResponseEntity.status(403).body("Bạn không có quyền xem thông tin của sự kiện này.");
        }

        List<Booking> eventBookings = bookingRepository.findByEventId(eventId);
        List<Map<String, Object>> detailsList = new ArrayList<>();

        for (Booking b : eventBookings) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", b.getId());
            map.put("customerName", b.getCustomerName());
            map.put("customerEmail", b.getCustomerEmail());
            map.put("customerPhone", b.getCustomerPhone());
            map.put("bookingTime", b.getBookingTime());
            map.put("guests", b.getGuests());
            map.put("depositAmount", b.getDepositAmount());
            map.put("paymentMethod", b.getPaymentMethod());
            map.put("paymentStatus", b.getPaymentStatus());
            map.put("status", b.getStatus());
            map.put("createdAt", b.getCreatedAt());
            detailsList.add(map);
        }
        return ResponseEntity.ok(detailsList);
    }
}
