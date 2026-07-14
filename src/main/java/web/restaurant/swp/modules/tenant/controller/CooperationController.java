package web.restaurant.swp.modules.tenant.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.auth.model.Role;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.RoleRepository;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.tenant.model.CooperationRequest;
import web.restaurant.swp.modules.tenant.model.Tenant;
import web.restaurant.swp.modules.tenant.repository.CooperationRequestRepository;
import web.restaurant.swp.modules.tenant.repository.TenantRepository;
import web.restaurant.swp.modules.booking.model.Booking;
import web.restaurant.swp.modules.booking.repository.BookingRepository;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class CooperationController {

    private final CooperationRequestRepository cooperationRequestRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final web.restaurant.swp.util.PayOSHelper payOSHelper;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final web.restaurant.swp.modules.booking.service.BookingService bookingService;

    private User getLoggedInUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @PostMapping("/api/cooperation/apply")
    @Transactional
    public ResponseEntity<?> applyCooperation(@RequestBody Map<String, String> payload) {
        User user = getLoggedInUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));
        }

        String businessName = payload.get("businessName");
        String domain = payload.get("domain");
        String contactPhone = payload.get("contactPhone");
        String requestType = payload.get("requestType");

        if (businessName == null || businessName.trim().isEmpty() ||
            contactPhone == null || contactPhone.trim().isEmpty() ||
            requestType == null || requestType.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng nhập đầy đủ các thông tin bắt buộc."));
        }

        double amount = 0.0;
        if ("EVENT_ONLY".equalsIgnoreCase(requestType)) {
            amount = 5000000.0;
        } else if ("APP_LIFETIME".equalsIgnoreCase(requestType)) {
            amount = 50000000.0;
        } else if ("APP_SUBSCRIPTION".equalsIgnoreCase(requestType)) {
            amount = 2000000.0;
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Gói dịch vụ không hợp lệ."));
        }

        CooperationRequest request = CooperationRequest.builder()
                .user(user)
                .businessName(businessName.trim())
                .domain(domain != null ? domain.trim() : "")
                .contactPhone(contactPhone.trim())
                .requestType(requestType.toUpperCase())
                .paymentAmount(amount)
                .status("PENDING")
                .build();

        cooperationRequestRepository.save(request);
        return ResponseEntity.ok(request);
    }

    @GetMapping("/api/cooperation/my-requests")
    public ResponseEntity<?> getMyRequests() {
        User user = getLoggedInUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));
        }

        List<CooperationRequest> requests = cooperationRequestRepository.findByUserEmailOrderByCreatedAtDesc(user.getEmail());
        List<Map<String, Object>> result = new ArrayList<>();
        for (CooperationRequest req : requests) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", req.getId());
            map.put("businessName", req.getBusinessName());
            map.put("domain", req.getDomain());
            map.put("contactPhone", req.getContactPhone());
            map.put("requestType", req.getRequestType());
            map.put("paymentAmount", req.getPaymentAmount());
            map.put("status", req.getStatus());
            map.put("createdAt", req.getCreatedAt());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/admin/cooperation")
    public ResponseEntity<?> getAllRequests() {
        List<CooperationRequest> requests = cooperationRequestRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (CooperationRequest req : requests) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", req.getId());
            map.put("businessName", req.getBusinessName());
            map.put("domain", req.getDomain());
            map.put("contactPhone", req.getContactPhone());
            map.put("requestType", req.getRequestType());
            map.put("paymentAmount", req.getPaymentAmount());
            map.put("status", req.getStatus());
            map.put("createdAt", req.getCreatedAt());
            map.put("requesterName", req.getUser().getName());
            map.put("requesterEmail", req.getUser().getEmail());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/admin/cooperation/{id}/approve")
    @Transactional
    public ResponseEntity<?> approveRequest(@PathVariable Long id) {
        Optional<CooperationRequest> reqOpt = cooperationRequestRepository.findById(id);
        if (reqOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CooperationRequest request = reqOpt.get();
        if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Yêu cầu đã được xử lý trước đó."));
        }

        User user = request.getUser();

        // 1. Create Tenant
        boolean isUsingSystemWeb = "APP_LIFETIME".equalsIgnoreCase(request.getRequestType()) || 
                                   "APP_SUBSCRIPTION".equalsIgnoreCase(request.getRequestType());
        Tenant tenant = Tenant.builder()
                .name(request.getBusinessName())
                .domain(request.getDomain() != null ? request.getDomain() : "")
                .isActive(true)
                .isUsingSystemWeb(isUsingSystemWeb)
                .build();
        tenant = tenantRepository.save(tenant);

        // 2. Assign Role and Tenant
        Role cooperatorRole = roleRepository.findByName("COOPERATOR")
                .orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò COOPERATOR"));
        
        user.getRoles().add(cooperatorRole);
        user.setTenant(tenant);
        userRepository.save(user);

        // 3. Update Request Status
        request.setStatus("APPROVED");
        cooperationRequestRepository.save(request);

        return ResponseEntity.ok(Map.of("message", "Đã phê duyệt yêu cầu hợp tác thành công."));
    }

    @PostMapping("/api/admin/cooperation/{id}/reject")
    @Transactional
    public ResponseEntity<?> rejectRequest(@PathVariable Long id) {
        Optional<CooperationRequest> reqOpt = cooperationRequestRepository.findById(id);
        if (reqOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CooperationRequest request = reqOpt.get();
        if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Yêu cầu đã được xử lý trước đó."));
        }

        request.setStatus("REJECTED");
        cooperationRequestRepository.save(request);

        return ResponseEntity.ok(Map.of("message", "Đã từ chối yêu cầu hợp tác."));
    }

    @PostMapping("/api/cooperation/payos/create-link")
    @Transactional
    public ResponseEntity<?> createPayOSLink(@RequestBody Map<String, String> payload) {
        User user = getLoggedInUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));
        }

        String businessName = payload.get("businessName");
        String domain = payload.get("domain");
        String contactPhone = payload.get("contactPhone");
        String requestType = payload.get("requestType");

        if (businessName == null || businessName.trim().isEmpty() ||
            contactPhone == null || contactPhone.trim().isEmpty() ||
            requestType == null || requestType.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng nhập đầy đủ các thông tin bắt buộc."));
        }

        double amount = 0.0;
        if ("EVENT_ONLY".equalsIgnoreCase(requestType)) {
            amount = 5000000.0;
        } else if ("APP_LIFETIME".equalsIgnoreCase(requestType)) {
            amount = 50000000.0;
        } else if ("APP_SUBSCRIPTION".equalsIgnoreCase(requestType)) {
            amount = 2000000.0;
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Gói dịch vụ không hợp lệ."));
        }

        long orderCode = web.restaurant.swp.util.PayOSHelper.generateOrderCode();

        CooperationRequest request = CooperationRequest.builder()
                .user(user)
                .businessName(businessName.trim())
                .domain(domain != null ? domain.trim() : "")
                .contactPhone(contactPhone.trim())
                .requestType(requestType.toUpperCase())
                .paymentAmount(amount)
                .orderCode(orderCode)
                .status("PAYMENT_PENDING")
                .build();

        cooperationRequestRepository.save(request);

        String returnUrl = "http://localhost:3000/profile?coopStatus=success";
        String cancelUrl = "http://localhost:3000/profile?coopStatus=cancel";
        
        try {
            Map<String, Object> payosData = payOSHelper.createPaymentLink(
                    orderCode,
                    amount,
                    "RMSCOOP" + request.getId(),
                    returnUrl,
                    cancelUrl
            );
            return ResponseEntity.ok(payosData);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Không thể tạo liên kết thanh toán PayOS: " + e.getMessage()));
        }
    }

    @PostMapping("/api/public/webhook/payos")
    @Transactional
    public ResponseEntity<?> receivePayOSWebhook(@RequestBody Map<String, Object> payload) {
        boolean verified = payOSHelper.verifyWebhookSignature(payload);
        if (!verified) {
            return ResponseEntity.status(401).body(Map.of("error", "Webhook signature verification failed"));
        }

        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data != null && data.containsKey("orderCode")) {
            Object codeObj = data.get("orderCode");
            Long orderCode;
            if (codeObj instanceof Number) {
                orderCode = ((Number) codeObj).longValue();
            } else {
                orderCode = Long.parseLong(codeObj.toString());
            }

            // Check if it matches a cooperation request
            Optional<CooperationRequest> reqOpt = cooperationRequestRepository.findByOrderCode(orderCode);
            if (reqOpt.isPresent()) {
                CooperationRequest request = reqOpt.get();
                if ("PAYMENT_PENDING".equalsIgnoreCase(request.getStatus())) {
                    request.setStatus("PENDING");
                    cooperationRequestRepository.save(request);
                }
            } else {
                // Otherwise, check if it matches a booking cọc/vé payment
                Optional<Booking> bookingOpt = bookingRepository.findByOrderCode(orderCode);
                if (bookingOpt.isPresent()) {
                    Booking b = bookingOpt.get();
                    if ("PENDING".equals(b.getPaymentStatus())) {
                        b.setPaymentStatus("PAID");
                        b.setDepositPaid(true);
                        Booking saved = bookingRepository.save(b);
                        bookingService.sendBookingConfirmationEmail(saved);
                    }
                }
            }
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    // Public endpoint: Register new Cooperator request
    @PostMapping("/api/public/cooperation/register")
    @Transactional
    public ResponseEntity<?> registerCooperator(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String password = payload.get("password");
        String name = payload.get("name");
        String businessName = payload.get("businessName");
        String contactPhone = payload.get("contactPhone");
        String requestType = payload.get("requestType");

        if (email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty() ||
            name == null || name.trim().isEmpty() ||
            businessName == null || businessName.trim().isEmpty() ||
            contactPhone == null || contactPhone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng nhập đầy đủ các thông tin bắt buộc."));
        }

        // Email validation
        if (!email.trim().matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email không đúng định dạng."));
        }

        // Password strength: min 8, 1 digit, 1 special char
        if (password.length() < 8 || !password.matches(".*\\d.*") || !password.matches(".*[!@#$%^&*()].*")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mật khẩu không đủ mạnh (tối thiểu 8 ký tự, 1 chữ số, 1 ký tự đặc biệt)."));
        }

        // Name validation
        if (!name.trim().matches("^[\\p{L}\\s']{2,100}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Họ và tên không hợp lệ (chỉ chứa chữ cái, khoảng trắng, từ 2-100 ký tự)."));
        }

        // Phone validation
        if (!contactPhone.trim().matches("^(0|\\+84)[35789][0-9]{8}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Số điện thoại liên hệ không hợp lệ (phải là số điện thoại Việt Nam hợp lệ)."));
        }

        // Business Name validation
        if (businessName.trim().length() < 3 || businessName.trim().length() > 150) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tên nhà hàng/chuỗi phải từ 3 đến 150 ký tự."));
        }

        if (userRepository.findByEmail(email.trim()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email đã tồn tại trên hệ thống."));
        }

        // 1. Create a regular CUSTOMER user (since they are not approved yet)
        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò CUSTOMER"));

        User user = User.builder()
                .email(email.trim())
                .password(passwordEncoder.encode(password))
                .name(name.trim())
                .phone(contactPhone.trim())
                .tenant(null) // No tenant yet
                .roles(new HashSet<>(Arrays.asList(customerRole)))
                .isActive(true)
                .build();
        user = userRepository.save(user);

        // 2. Create cooperation request with PENDING status
        double amount = 0.0;
        if ("EVENT_ONLY".equalsIgnoreCase(requestType)) {
            amount = 5000000.0;
        } else if ("APP_LIFETIME".equalsIgnoreCase(requestType)) {
            amount = 50000000.0;
        } else if ("APP_SUBSCRIPTION".equalsIgnoreCase(requestType)) {
            amount = 2000000.0;
        }

        CooperationRequest request = CooperationRequest.builder()
                .user(user)
                .businessName(businessName.trim())
                .domain("")
                .contactPhone(contactPhone.trim())
                .requestType(requestType != null ? requestType.toUpperCase() : "APP_SUBSCRIPTION")
                .paymentAmount(amount)
                .status("PENDING") // Send to overall admin for approval
                .build();
        cooperationRequestRepository.save(request);

        return ResponseEntity.ok(Map.of("message", "Đăng ký thành công! Yêu cầu hợp tác đã được gửi tới Ban Quản Trị để phê duyệt quyền Đối tác."));
    }

    // Secured endpoint: Direct Upgrade request from CUSTOMER (creates PENDING request)
    @PostMapping("/api/cooperation/quick-upgrade")
    @Transactional
    public ResponseEntity<?> quickUpgrade(@RequestBody Map<String, String> payload) {
        User user = getLoggedInUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));
        }

        String businessName = payload.get("businessName");
        String contactPhone = payload.get("contactPhone");
        String requestType = payload.get("requestType");

        if (businessName == null || businessName.trim().isEmpty() ||
            contactPhone == null || contactPhone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng nhập đầy đủ các thông tin bắt buộc."));
        }

        // Create a CooperationRequest as PENDING directly so it goes to Admin review
        double amount = 0.0;
        if ("EVENT_ONLY".equalsIgnoreCase(requestType)) {
            amount = 5000000.0;
        } else if ("APP_LIFETIME".equalsIgnoreCase(requestType)) {
            amount = 50000000.0;
        } else if ("APP_SUBSCRIPTION".equalsIgnoreCase(requestType)) {
            amount = 2000000.0;
        }

        CooperationRequest request = CooperationRequest.builder()
                .user(user)
                .businessName(businessName.trim())
                .domain("")
                .contactPhone(contactPhone.trim())
                .requestType(requestType != null ? requestType.toUpperCase() : "APP_SUBSCRIPTION")
                .paymentAmount(amount)
                .status("PENDING") // Pending admin approval
                .build();
        cooperationRequestRepository.save(request);

        return ResponseEntity.ok(Map.of(
            "message", "Yêu cầu hợp tác đã được gửi lên Ban Quản Trị thành công! Vui lòng chờ phê duyệt."
        ));
    }
}
