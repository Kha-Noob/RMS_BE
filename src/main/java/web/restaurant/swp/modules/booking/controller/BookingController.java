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
import web.restaurant.swp.modules.event.model.Event;
import web.restaurant.swp.modules.event.repository.EventRepository;
import web.restaurant.swp.modules.inventory.model.Product;
import web.restaurant.swp.modules.inventory.model.ProductVariant;
import web.restaurant.swp.modules.inventory.repository.ProductRepository;
import web.restaurant.swp.modules.inventory.repository.ProductVariantRepository;
import web.restaurant.swp.modules.branch.model.BankSetting;
import web.restaurant.swp.modules.branch.repository.BankSettingRepository;
import web.restaurant.swp.modules.pos.repository.TableSessionRepository;
import web.restaurant.swp.modules.pos.model.TableSession;

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
    private final EventRepository eventRepository;
    private final BankSettingRepository bankSettingRepository;
    private final web.restaurant.swp.util.PayOSHelper payOSHelper;
    private final TableSessionRepository tableSessionRepository;

    /**
     * Get all active branches for reservation.
     */
    @GetMapping("/api/public/branches")
    public ResponseEntity<?> getPublicBranches(@RequestParam(required = false) String tenantId) {
        String targetTenantId = (tenantId != null && !tenantId.trim().isEmpty()) ? tenantId.trim() : "tenant-1";
        List<Branch> branches = branchRepository.findByTenantTenantIdAndIsActiveTrue(targetTenantId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Branch b : branches) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("branchId", b.getBranchId());
            map.put("name", b.getName());
            map.put("address", b.getAddress());
            map.put("phone", b.getPhone());

            // Resolve bank settings (try Branch BankSetting first, fallback to Tenant Bank details)
            Optional<BankSetting> settingOpt = bankSettingRepository.findByBranchBranchId(b.getBranchId());
            if (settingOpt.isPresent()) {
                BankSetting bs = settingOpt.get();
                map.put("bankName", bs.getBankName() != null ? bs.getBankName() : "");
                map.put("bankAccountNo", bs.getAccountNumber() != null ? bs.getAccountNumber() : "");
                map.put("bankAccountName", bs.getAccountHolder() != null ? bs.getAccountHolder() : "");
                map.put("bankBranch", bs.getBankCode() != null ? bs.getBankCode() : "");
            } else if (b.getTenant() != null) {
                Tenant tenant = b.getTenant();
                map.put("bankName", tenant.getBankName() != null ? tenant.getBankName() : "");
                map.put("bankAccountNo", tenant.getBankAccountNo() != null ? tenant.getBankAccountNo() : "");
                map.put("bankAccountName", tenant.getBankAccountName() != null ? tenant.getBankAccountName() : "");
                map.put("bankBranch", tenant.getBankBranch() != null ? tenant.getBankBranch() : "");
            } else {
                map.put("bankName", "");
                map.put("bankAccountNo", "");
                map.put("bankAccountName", "");
                map.put("bankBranch", "");
            }
            result.add(map);
        }
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
            
            // Fetch all currently occupied tables with their check-in times formatted
            List<TableSession> activeSessions = tableSessionRepository.findByTableRoomBranchBranchIdAndStatus(branchId, "ACTIVE");
            Map<Long, String> occupiedTimes = new HashMap<>();
            for (TableSession s : activeSessions) {
                if (s.getCheckInTime() != null) {
                    occupiedTimes.put(s.getTable().getId(), s.getCheckInTime().toString());
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "bookedTableIds", bookedTableIds,
                "occupiedTables", occupiedTimes
            ));
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
            if (booking.getEventId() != null) {
                Optional<Event> eventOpt = eventRepository.findById(booking.getEventId());
                if (eventOpt.isPresent()) {
                    Event event = eventOpt.get();
                    if (event.getBookingDeadline() != null && LocalDateTime.now().isAfter(event.getBookingDeadline())) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Thời hạn đăng ký vé sự kiện này đã kết thúc!"));
                    }
                }
            }
            booking.setSource("ONLINE");
            
            // If payment is required via QR, pre-generate an orderCode for PayOS
            if (booking.getDepositAmount() != null && booking.getDepositAmount() > 0 && "QR_PAY".equalsIgnoreCase(booking.getPaymentMethod())) {
                long orderCode = web.restaurant.swp.util.PayOSHelper.generateOrderCode();
                booking.setOrderCode(orderCode);
            }
            
            Booking created = bookingService.createBooking(booking);
            
            // If orderCode is present, attempt to create PayOS payment link
            if (created.getOrderCode() != null) {
                try {
                    String returnUrl = "http://localhost:3000/booking?status=success";
                    String cancelUrl = "http://localhost:3000/booking?status=cancel";
                    if (created.getEventId() != null) {
                        returnUrl = "http://localhost:3000/events?status=success";
                        cancelUrl = "http://localhost:3000/events?status=cancel";
                    }
                    
                    Map<String, Object> payosData = payOSHelper.createPaymentLink(
                            created.getOrderCode(),
                            created.getDepositAmount(),
                            "RMSDC" + created.getId(),
                            returnUrl,
                            cancelUrl
                    );
                    if (payosData != null && payosData.containsKey("checkoutUrl")) {
                        created.setCheckoutUrl((String) payosData.get("checkoutUrl"));
                    }
                } catch (Exception e) {
                    log.warn("[PAYOS BOOKING] Could not create PayOS payment link: {}. Falling back to standard QR code.", e.getMessage());
                }
            }
            
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
            @RequestParam(required = false) Long eventId,
            @RequestParam String date) {
        try {
            java.time.LocalDate localDate = java.time.LocalDate.parse(date);
            java.time.LocalDateTime start = localDate.atStartOfDay();
            java.time.LocalDateTime end = localDate.atTime(java.time.LocalTime.MAX);
            
            List<Booking> bookings = bookingRepository.findByBranchIdAndBookingTimeBetween(branchId, start, end);
            
            int bookedGuests = bookings.stream()
                    .filter(b -> {
                        if (eventId != null) {
                            return b.getEventId() != null && b.getEventId().equals(eventId);
                        }
                        return b.getNotes() != null && b.getNotes().toLowerCase().contains(eventTitle.toLowerCase());
                    })
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
                .map(b -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", b.getId());
                    map.put("customerName", b.getCustomerName());
                    map.put("customerPhone", b.getCustomerPhone());
                    map.put("customerEmail", b.getCustomerEmail());
                    map.put("bookingTime", b.getBookingTime());
                    map.put("guests", b.getGuests());
                    map.put("status", b.getStatus());
                    map.put("source", b.getSource());
                    map.put("depositPaid", b.getDepositPaid());
                    map.put("branchId", b.getBranchId());
                    map.put("notes", b.getNotes());
                    map.put("tableId", b.getTableId());
                    map.put("tableLabel", b.getTableLabel());
                    map.put("dietaryNotes", b.getDietaryNotes());
                    map.put("allergyPeanut", b.getAllergyPeanut());
                    map.put("allergyGluten", b.getAllergyGluten());
                    map.put("allergyOthers", b.getAllergyOthers());
                    map.put("depositAmount", b.getDepositAmount());
                    map.put("paymentMethod", b.getPaymentMethod());
                    map.put("paymentStatus", b.getPaymentStatus());
                    map.put("orderCode", b.getOrderCode());
                    map.put("durationMinutes", b.getDurationMinutes());
                    map.put("createdAt", b.getCreatedAt());
                    map.put("updatedAt", b.getUpdatedAt());
                    map.put("eventId", b.getEventId());

                    // Enrich Branch Details
                    branchRepository.findById(b.getBranchId()).ifPresent(branch -> {
                        map.put("branchName", branch.getName());
                        map.put("branchAddress", branch.getAddress());
                    });

                    // Enrich Event details if present
                    if (b.getEventId() != null) {
                        eventRepository.findById(b.getEventId()).ifPresent(event -> {
                            map.put("eventTitle", event.getTitle());
                            map.put("eventDate", event.getDate());
                            map.put("eventTime", event.getTime());
                            map.put("eventLocation", event.getLocation());
                            map.put("eventDescription", event.getDescription());
                        });
                    }

                    return ResponseEntity.ok(map);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/pos/bookings/{id}/check-in")
    public ResponseEntity<?> checkInBooking(@PathVariable Long id) {
        return bookingRepository.findById(id)
                .map(b -> {
                    b.setStatus("CHECKED_IN");
                    b.setUpdatedAt(LocalDateTime.now());
                    Booking saved = bookingRepository.save(b);
                    log.info("[CHECK-IN] Booking #{} successfully checked-in by staff", id);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT API to manually confirm payment of a booking (simulates webhook / bank transfer notification).
     */
    @PutMapping("/api/public/bookings/{id}/confirm-payment")
    public ResponseEntity<?> confirmBookingPayment(@PathVariable Long id) {
        return bookingRepository.findById(id)
                .map(b -> {
                    b.setPaymentStatus("PAID");
                    b.setDepositPaid(true);
                    Booking saved = bookingRepository.save(b);
                    log.info("[SIMULATION WEBHOOK] Booking #{} successfully marked as PAID", id);
                    bookingService.sendBookingConfirmationEmail(saved);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST API for banking webhooks (Casso / PayOS style auto-matching simulation).
     * Automatically parses bank statements and approves matching bookings.
     */
    @PostMapping("/api/public/webhook/banking")
    public ResponseEntity<?> receiveBankingWebhook(@RequestBody Map<String, Object> payload) {
        log.info("[BANKING WEBHOOK] Received banking transaction notification: {}", payload);
        try {
            if (payload.containsKey("data")) {
                List<Map<String, Object>> transactions = (List<Map<String, Object>>) payload.get("data");
                for (Map<String, Object> tx : transactions) {
                    String description = (String) tx.get("description");
                    Number amountNum = (Number) tx.get("amount");
                    double amount = amountNum != null ? amountNum.doubleValue() : 0.0;
                    
                    if (description != null) {
                        processBankingTransaction(description, amount);
                    }
                }
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("[BANKING WEBHOOK] Error processing banking transaction: ", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private void processBankingTransaction(String memo, double amount) {
        String memoUpper = memo.toUpperCase();
        log.info("[BANKING MATCHING] Matching transaction. Memo: '{}', Amount: {}", memo, amount);
        
        // 1. Match by Booking ID (e.g. VE29, DC29, VE 29, DC 29, BK29)
        Long bookingId = extractBookingId(memoUpper);
        if (bookingId != null) {
            Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
            if (bookingOpt.isPresent()) {
                Booking b = bookingOpt.get();
                if ("PENDING".equals(b.getPaymentStatus())) {
                    b.setPaymentStatus("PAID");
                    b.setDepositPaid(true);
                    Booking saved = bookingRepository.save(b);
                    log.info("[BANKING MATCHING] Auto-matched Booking ID #{} from memo successfully!", bookingId);
                    bookingService.sendBookingConfirmationEmail(saved);
                    return;
                }
            }
        }
        
        // 2. Match by Phone Number (extract last 9 digits of phone)
        String phone = extractPhoneNumber(memoUpper);
        if (phone != null && !phone.isEmpty()) {
            final String normPhone = phone;
            List<Booking> pendingBookings = bookingRepository.findAll().stream()
                    .filter(b -> "PENDING".equals(b.getPaymentStatus()))
                    .filter(b -> b.getCustomerPhone() != null && b.getCustomerPhone().replaceAll("[^0-9]", "").endsWith(normPhone))
                    .collect(java.util.stream.Collectors.toList());
            
            if (!pendingBookings.isEmpty()) {
                Booking matched = pendingBookings.get(0);
                for (Booking b : pendingBookings) {
                    if (Math.abs(b.getDepositAmount() - amount) < 100) {
                        matched = b;
                        break;
                    }
                }
                matched.setPaymentStatus("PAID");
                matched.setDepositPaid(true);
                Booking saved = bookingRepository.save(matched);
                log.info("[BANKING MATCHING] Auto-matched Booking ID #{} via customer phone ending in '{}' successfully!", matched.getId(), normPhone);
                bookingService.sendBookingConfirmationEmail(saved);
                return;
            }
        }
        
        log.warn("[BANKING MATCHING] Could not match transaction memo '{}' with any pending booking.", memo);
    }

    private Long extractBookingId(String memo) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(VE|DC|BK|ID|MÃ|MA|BOOKING)\\s*(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(memo);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(2));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        pattern = java.util.regex.Pattern.compile("#(\\d+)");
        matcher = pattern.matcher(memo);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String extractPhoneNumber(String memo) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(0|84|\\+84)\\d{8,10}");
        java.util.regex.Matcher matcher = pattern.matcher(memo);
        if (matcher.find()) {
            String p = matcher.group();
            return p.length() > 9 ? p.substring(p.length() - 9) : p;
        }
        return null;
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
            
            // If the updated booking requires a deposit and has an orderCode, and isn't paid yet, create the PayOS payment link
            if (updated.getOrderCode() != null && !Boolean.TRUE.equals(updated.getDepositPaid()) && 
                "QR_PAY".equalsIgnoreCase(updated.getPaymentMethod())) {
                try {
                    String returnUrl = "http://localhost:3000/booking-history?status=success";
                    String cancelUrl = "http://localhost:3000/booking-history?status=cancel";
                    
                    double amount = (updated.getPendingUpdateJson() != null) ? 100000.0 : updated.getDepositAmount();
                    Map<String, Object> payosData = payOSHelper.createPaymentLink(
                            updated.getOrderCode(),
                            amount,
                            "RMSDC" + updated.getId(),
                            returnUrl,
                            cancelUrl
                    );
                    if (payosData != null && payosData.containsKey("checkoutUrl")) {
                        updated.setCheckoutUrl((String) payosData.get("checkoutUrl"));
                    }
                } catch (Exception e) {
                    log.warn("[PAYOS BOOKING UPDATE] Could not create PayOS payment link: {}. Falling back to standard QR code.", e.getMessage());
                    updated.setCheckoutUrl("http://localhost:3000/booking-history?fallback-pay=true");
                }
            }
            
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

        List<Map<String, Object>> enrichedList = new ArrayList<>();
        for (Booking b : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", b.getId());
            map.put("customerName", b.getCustomerName());
            map.put("customerPhone", b.getCustomerPhone());
            map.put("customerEmail", b.getCustomerEmail());
            map.put("bookingTime", b.getBookingTime());
            map.put("guests", b.getGuests());
            map.put("status", b.getStatus());
            map.put("source", b.getSource());
            map.put("depositPaid", b.getDepositPaid());
            map.put("branchId", b.getBranchId());
            map.put("notes", b.getNotes());
            map.put("tableId", b.getTableId());
            map.put("tableLabel", b.getTableLabel());
            map.put("dietaryNotes", b.getDietaryNotes());
            map.put("allergyPeanut", b.getAllergyPeanut());
            map.put("allergyGluten", b.getAllergyGluten());
            map.put("allergyOthers", b.getAllergyOthers());
            map.put("orderedItemsJson", b.getOrderedItemsJson());
            map.put("depositAmount", b.getDepositAmount());
            map.put("paymentMethod", b.getPaymentMethod());
            map.put("paymentStatus", b.getPaymentStatus());
            map.put("durationMinutes", b.getDurationMinutes());
            map.put("createdAt", b.getCreatedAt());
            map.put("eventId", b.getEventId());
            map.put("updatedAt", b.getUpdatedAt());

            // Enrich Branch details
            if (b.getBranchId() != null) {
                Optional<Branch> branchOpt = branchRepository.findById(b.getBranchId());
                if (branchOpt.isPresent()) {
                    Branch br = branchOpt.get();
                    map.put("branchName", br.getName());
                    map.put("branchAddress", br.getAddress());
                } else {
                    map.put("branchName", b.getBranchId());
                    map.put("branchAddress", "");
                }
            } else {
                map.put("branchName", "");
                map.put("branchAddress", "");
            }

            // Enrich Event details
            if (b.getEventId() != null) {
                Optional<Event> eventOpt = eventRepository.findById(b.getEventId());
                if (eventOpt.isPresent()) {
                    Event ev = eventOpt.get();
                    map.put("eventTitle", ev.getTitle());
                    map.put("eventLocation", ev.getLocation());
                    map.put("eventDate", ev.getDate());
                    map.put("eventTime", ev.getTime());
                }
            }

            enrichedList.add(map);
        }

        return ResponseEntity.ok(enrichedList);
    }
}
