package web.restaurant.swp.modules.booking.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.booking.model.Booking;
import web.restaurant.swp.modules.booking.repository.BookingRepository;
import web.restaurant.swp.modules.booking.service.BookingService;
import web.restaurant.swp.modules.branch.model.Branch;
import web.restaurant.swp.modules.branch.repository.BranchRepository;
import web.restaurant.swp.modules.inventory.model.Product;
import web.restaurant.swp.modules.inventory.model.ProductVariant;
import web.restaurant.swp.modules.inventory.repository.ProductRepository;
import web.restaurant.swp.modules.inventory.repository.ProductVariantRepository;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class BookingController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    /**
     * Get all active branches for reservation.
     */
    @GetMapping("/api/public/branches")
    public ResponseEntity<?> getPublicBranches() {
        List<Branch> branches = branchRepository.findByTenantTenantIdAndIsActiveTrue("tenant-1");
        List<Map<String, Object>> result = branches.stream().map(b -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("branchId", b.getBranchId());
            map.put("name", b.getName());
            map.put("address", b.getAddress());
            map.put("phone", b.getPhone());
            return map;
        }).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Get the active menu for a specific branch (or default tenant) with variant prices and stock status.
     * We simulate stock status dynamically so it works in real time.
     */
    @GetMapping("/api/public/branches/{branchId}/menu")
    public ResponseEntity<?> getBranchMenu(@PathVariable String branchId) {
        List<Product> products = productRepository.findByTenantTenantIdAndIsActiveTrue("tenant-1");
        List<Map<String, Object>> menuList = new ArrayList<>();

        for (Product p : products) {
            List<ProductVariant> variants = productVariantRepository.findByProductId(p.getId());
            if (variants.isEmpty()) continue;

            List<Map<String, Object>> variantList = new ArrayList<>();
            for (ProductVariant v : variants) {
                // Filter by branch override if set
                if (v.getBranchId() != null && !v.getBranchId().equals(branchId)) {
                    continue;
                }
                
                Map<String, Object> vMap = new LinkedHashMap<>();
                vMap.put("id", v.getId());
                vMap.put("name", v.getName());
                vMap.put("price", v.getPrice());
                vMap.put("sku", v.getSku());
                vMap.put("isTopping", v.isTopping());
                
                // Simulate "Out of Stock" dynamically for specific variants (e.g. ID % 7 == 0)
                boolean inStock = (v.getId() % 7 != 0);
                vMap.put("inStock", inStock);
                
                variantList.add(vMap);
            }

            if (variantList.isEmpty()) continue;

            Map<String, Object> pMap = new LinkedHashMap<>();
            pMap.put("id", p.getId());
            pMap.put("name", p.getName());
            pMap.put("description", p.getDescription());
            pMap.put("imagePath", p.getImagePath());
            pMap.put("categoryName", p.getCategory() != null ? p.getCategory().getName() : "General");
            pMap.put("variants", variantList);

            menuList.add(pMap);
        }

        return ResponseEntity.ok(menuList);
    }

    /**
     * Check which table IDs are already reserved for a given date and time.
     */
    @GetMapping("/api/public/branches/{branchId}/tables/availability")
    public ResponseEntity<?> getTableAvailability(
            @PathVariable String branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime time) {
        try {
            List<Long> bookedTableIds = bookingService.getBookedTableIds(branchId, time);
            return ResponseEntity.ok(Map.of("bookedTableIds", bookedTableIds));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Submit a new booking.
     */
    @PostMapping("/api/public/bookings")
    public ResponseEntity<?> createPublicBooking(@RequestBody Booking booking) {
        try {
            booking.setSource("ONLINE");
            Booking created = bookingService.createBooking(booking);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get the current booked guests count for a specific event on a date.
     */
    @GetMapping("/api/public/bookings-capacity/event")
    public ResponseEntity<?> getEventCapacity(
            @RequestParam String branchId,
            @RequestParam String eventTitle,
            @RequestParam String date) {
        try {
            java.time.LocalDate localDate = java.time.LocalDate.parse(date);
            java.time.LocalDateTime start = localDate.atStartOfDay();
            java.time.LocalDateTime end = localDate.atTime(java.time.LocalTime.MAX);
            
            List<Booking> bookings = bookingRepository.findByBranchIdAndBookingTimeBetween(branchId, start, end);
            
            int bookedGuests = bookings.stream()
                    .filter(b -> b.getNotes() != null && b.getNotes().toLowerCase().contains(eventTitle.toLowerCase()))
                    .filter(b -> !"CANCELLED".equals(b.getStatus()) && !"NO_SHOW".equals(b.getStatus()))
                    .mapToInt(Booking::getGuests)
                    .sum();
                    
            return ResponseEntity.ok(java.util.Map.of("bookedGuests", bookedGuests));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get details of a specific booking.
     */
    @GetMapping("/api/public/bookings/{id}")
    public ResponseEntity<?> getBookingDetails(@PathVariable Long id) {
        return bookingRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Modify an existing booking (US#7).
     */
    @PutMapping("/api/public/bookings/{id}")
    public ResponseEntity<?> updateBookingDetails(
            @PathVariable Long id,
            @RequestBody Booking booking) {
        try {
            Booking updated = bookingService.updateBooking(id, booking);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cancel an existing booking (US#5 & US#7).
     */
    @DeleteMapping("/api/public/bookings/{id}")
    public ResponseEntity<?> cancelBookingDetails(@PathVariable Long id) {
        try {
            Booking cancelled = bookingService.cancelBooking(id);
            return ResponseEntity.ok(Map.of(
                    "message", "Đặt bàn đã được hủy thành công!",
                    "booking", cancelled
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Fetch bookings by customer phone or email (US#7).
     */
    @GetMapping("/api/public/bookings/customer")
    public ResponseEntity<?> getCustomerBookings(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email) {
        List<Booking> list = new ArrayList<>();
        if (phone != null && !phone.trim().isEmpty()) {
            list = bookingRepository.findByCustomerPhoneOrderByBookingTimeDesc(phone);
        } else if (email != null && !email.trim().isEmpty()) {
            list = bookingRepository.findByCustomerEmailOrderByBookingTimeDesc(email);
        }
        return ResponseEntity.ok(list);
    }
}
