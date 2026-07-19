package web.restaurant.swp.modules.pos.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import web.restaurant.swp.config.KdsWebSocketHandler;
import web.restaurant.swp.modules.auth.model.*;
import web.restaurant.swp.modules.auth.repository.*;
import web.restaurant.swp.modules.auth.service.*;
import web.restaurant.swp.modules.pos.model.*;
import web.restaurant.swp.modules.pos.repository.*;
import web.restaurant.swp.modules.pos.service.*;
import web.restaurant.swp.modules.inventory.model.*;
import web.restaurant.swp.modules.inventory.repository.*;
import web.restaurant.swp.modules.inventory.service.*;
import web.restaurant.swp.modules.procurement.model.*;
import web.restaurant.swp.modules.procurement.repository.*;
import web.restaurant.swp.modules.procurement.service.*;
import web.restaurant.swp.modules.hr.model.*;
import web.restaurant.swp.modules.hr.repository.*;
import web.restaurant.swp.modules.hr.service.*;
import web.restaurant.swp.modules.loyalty.model.*;
import web.restaurant.swp.modules.loyalty.repository.*;
import web.restaurant.swp.modules.loyalty.service.*;
import web.restaurant.swp.modules.promotion.model.*;
import web.restaurant.swp.modules.promotion.repository.*;
import web.restaurant.swp.modules.promotion.service.*;
import web.restaurant.swp.modules.analytics.service.*;
import web.restaurant.swp.modules.branch.model.*;
import web.restaurant.swp.modules.branch.repository.*;
import web.restaurant.swp.modules.branch.service.BranchAccessService;
import web.restaurant.swp.modules.floorplan.model.FloorPlan;
import web.restaurant.swp.modules.floorplan.model.FloorPlanObject;
import web.restaurant.swp.modules.floorplan.repository.FloorPlanRepository;
import web.restaurant.swp.modules.floorplan.repository.FloorPlanObjectRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import web.restaurant.swp.util.PayOSHelper;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PosController {

    private final TableRepository tableRepository;
    private final TableSessionRepository tableSessionRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final UserSessionRepository userSessionRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrderService orderService;
    private final BankSettingRepository bankSettingRepository;
    private final AuthService authService;
    private final AuditLogRepository auditLogRepository;
    private final BranchAccessService branchAccessService;
    private final FloorPlanRepository floorPlanRepository;
    private final FloorPlanObjectRepository floorPlanObjectRepository;
    private final PayOSHelper payOSHelper;

    private User getLoggedInUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private String getActiveBranchId() {
        return web.restaurant.swp.config.BranchContext.getActiveBranchId(getLoggedInUser());
    }

    private String getActiveTenantId() {
        User user = getLoggedInUser();
        if (user != null && user.getTenant() != null) {
            return user.getTenant().getTenantId();
        }
        return "tenant-1";
    }

    private ResponseEntity<?> message(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    private boolean canManageRoomsAndTables(User user) {
        return user != null && user.getRoles().stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()));
    }

    private String normalizeRequiredName(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeTableStyle(String tableStyle) {
        String normalized = tableStyle == null || tableStyle.trim().isEmpty()
                ? "ROUND"
                : tableStyle.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ROUND", "SQUARE", "RECTANGLE", "VIP").contains(normalized)) {
            throw new IllegalArgumentException("Ki?u b?n kh?ng h?p l?.");
        }
        return normalized;
    }

    private String shapeForTableStyle(String tableStyle) {
        return "ROUND".equalsIgnoreCase(tableStyle) ? "circle" : "rectangle";
    }

    private void syncFloorPlanObjectsForTable(TableEntity table) {
        List<FloorPlanObject> objects = floorPlanObjectRepository.findByTableId(table.getId());
        for (FloorPlanObject object : objects) {
            object.setLabel(table.getDisplayLabel() != null && !table.getDisplayLabel().isBlank()
                    ? table.getDisplayLabel()
                    : table.getName());
            object.setShape(shapeForTableStyle(table.getTableStyle()));
            Map<String, Object> metadata = object.getMetadataJson() != null
                    ? new LinkedHashMap<>(object.getMetadataJson())
                    : new LinkedHashMap<>();
            metadata.put("tableEntityId", table.getId());
            metadata.put("tableId", table.getId());
            metadata.put("tableName", table.getName());
            metadata.put("capacity", table.getCapacity());
            metadata.put("tableStyle", table.getTableStyle());
            object.setMetadataJson(metadata);
        }
        floorPlanObjectRepository.saveAll(objects);
    }

    @GetMapping("/api/pos/session/active")
    public ResponseEntity<?> getActiveSession(@RequestParam Long tableId) {
        Optional<TableSession> sessionOpt = tableSessionRepository.findByTableIdAndStatus(tableId, "ACTIVE");
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TableSession session = sessionOpt.get();

        String entityBranchId = session.getTable().getRoom().getBranch().getBranchId();
        BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
        branchAccessService.validateEntityBranch(entityBranchId, error);
        if (error.hasError()) return error.toResponse();

        List<Order> orders = orderRepository.findBySessionId(session.getId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.getId());
        response.put("tableId", session.getTable().getId());
        response.put("tableName", session.getTable().getName());
        response.put("status", session.getStatus());
        
        List<Map<String, Object>> cartItems = new ArrayList<>();
        double total = 0.0;
        
        for (Order order : orders) {
            String s = order.getStatus();
            if ("PENDING".equalsIgnoreCase(s) || "SENT".equalsIgnoreCase(s)
                    || "COOKING".equalsIgnoreCase(s) || "READY".equalsIgnoreCase(s)
                    || "SERVED".equalsIgnoreCase(s)) {
                List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
                for (OrderDetail detail : details) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("detailId", detail.getId());
                    item.put("productName", detail.getVariant().getProduct().getName());
                    item.put("variantName", detail.getVariant().getName());
                    item.put("price", detail.getPrice());
                    item.put("quantity", detail.getQuantity());
                    item.put("status", detail.getStatus());
                    item.put("notes", detail.getNotes());
                    cartItems.add(item);
                    total += detail.getPrice() * detail.getQuantity();
                }
            }
        }
        response.put("items", cartItems);
        response.put("total", total);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/pos/session/open")
    public ResponseEntity<?> openSession(@RequestParam Long tableId, @RequestParam(required = false) Long customerId) {
        try {
            TableEntity table = tableRepository.findById(tableId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bàn."));
            String entityBranchId = table.getRoom().getBranch().getBranchId();
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(entityBranchId, error);
            if (error.hasError()) return error.toResponse();

            TableSession session = orderService.openTableSession(tableId, customerId);
            User user = getLoggedInUser();
            authService.logAudit(user, "OPEN_SESSION", "Order", session.getId().toString(),
                "M? ca (Check-in) cho b?n " + session.getTable().getName(), "127.0.0.1", session.getTable().getRoom().getBranch().getBranchId());
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/order/add")
    public ResponseEntity<?> addToCart(@RequestParam Long sessionId, @RequestParam Long variantId, @RequestParam int quantity, @RequestParam(required = false, defaultValue = "") String notes) {
        try {
            TableSession sess = tableSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phiên."));
            String entityBranchId = sess.getTable().getRoom().getBranch().getBranchId();
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(entityBranchId, error);
            if (error.hasError()) return error.toResponse();

            OrderDetail detail = orderService.addItemToSession(sessionId, variantId, quantity, notes);
            User user = getLoggedInUser();
            authService.logAudit(user, "ORDER_ADD_ITEM", "Order", detail.getOrder().getId().toString(),
                "Th?m m?n: " + quantity + "x " + detail.getVariant().getProduct().getName() + " (" + detail.getVariant().getName() + ") v?o b?n " + detail.getOrder().getSession().getTable().getName(),
                "127.0.0.1", detail.getOrder().getBranchId());
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/api/pos/session/{sessionId}/item/{detailId}")
    public ResponseEntity<?> updateCartItem(
            @PathVariable Long sessionId,
            @PathVariable Long detailId,
            @RequestParam int quantity) {
        try {
            TableSession sess = tableSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            String entityBranchId = sess.getTable().getRoom().getBranch().getBranchId();
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(entityBranchId, error);
            if (error.hasError()) return error.toResponse();

            OrderDetail detail = orderDetailRepository.findById(detailId)
                    .orElseThrow(() -> new RuntimeException("Cart item not found"));

            if (quantity <= 0) {
                orderDetailRepository.delete(detail);
            } else {
                detail.setQuantity(quantity);
                orderDetailRepository.save(detail);
            }

            // Update order total
            Order order = detail.getOrder();
            double orderTotal = orderDetailRepository.findByOrderId(order.getId()).stream()
                    .mapToDouble(d -> d.getPrice() * d.getQuantity())
                    .sum();
            order.setTotalAmount(orderTotal);
            orderRepository.save(order);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/api/pos/session/{sessionId}/item/{detailId}")
    public ResponseEntity<?> deleteCartItem(
            @PathVariable Long sessionId,
            @PathVariable Long detailId) {
        try {
            TableSession sess = tableSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            String entityBranchId = sess.getTable().getRoom().getBranch().getBranchId();
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(entityBranchId, error);
            if (error.hasError()) return error.toResponse();

            OrderDetail detail = orderDetailRepository.findById(detailId)
                    .orElseThrow(() -> new RuntimeException("Cart item not found"));

            Order order = detail.getOrder();
            orderDetailRepository.delete(detail);

            // Update order total
            double orderTotal = orderDetailRepository.findByOrderId(order.getId()).stream()
                    .mapToDouble(d -> d.getPrice() * d.getQuantity())
                    .sum();
            order.setTotalAmount(orderTotal);
            orderRepository.save(order);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/order/send")
    public ResponseEntity<?> sendToKds(@RequestParam Long sessionId) {
        try {
            TableSession session = tableSessionRepository.findById(sessionId).orElse(null);
            if (session != null) {
                String entityBranchId = session.getTable().getRoom().getBranch().getBranchId();
                BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
                branchAccessService.validateEntityBranch(entityBranchId, error);
                if (error.hasError()) return error.toResponse();
            }

            orderService.sendToKitchen(sessionId);
            User user = getLoggedInUser();
            String branchId = (session != null) ? session.getTable().getRoom().getBranch().getBranchId() : getActiveBranchId();
            authService.logAudit(user, "ORDER_SEND_KITCHEN", "Order", sessionId.toString(),
                "G?i y?u c?u ch? bi?n m?n ?n b?n " + (session != null ? session.getTable().getName() : sessionId) + " xu?ng b?p",
                "127.0.0.1", branchId);
            KdsWebSocketHandler.broadcast("NEW_ORDER_SUBMITTED");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/bill/merge")
    public ResponseEntity<?> mergeBill(@RequestParam Long sourceSessionId, @RequestParam Long targetSessionId) {
        try {
            TableSession src = tableSessionRepository.findById(sourceSessionId).orElse(null);
            TableSession tgt = tableSessionRepository.findById(targetSessionId).orElse(null);

            if (tgt != null) {
                String entityBranchId = tgt.getTable().getRoom().getBranch().getBranchId();
                BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
                branchAccessService.validateEntityBranch(entityBranchId, error);
                if (error.hasError()) return error.toResponse();
            }

            orderService.mergeBill(sourceSessionId, targetSessionId);
            User user = getLoggedInUser();
            String branchId = (tgt != null) ? tgt.getTable().getRoom().getBranch().getBranchId() : getActiveBranchId();
            authService.logAudit(user, "BILL_MERGE", "Order", targetSessionId.toString(),
                "Gh?p h?a ??n t? b?n " + (src != null ? src.getTable().getName() : sourceSessionId) + " sang b?n " + (tgt != null ? tgt.getTable().getName() : targetSessionId),
                "127.0.0.1", branchId);
            KdsWebSocketHandler.broadcast("ORDER_STATE_CHANGED");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/bill/split")
    public ResponseEntity<?> splitBill(@RequestParam Long sessionId, @RequestParam String detailIds) {
        try {
            TableSession original = tableSessionRepository.findById(sessionId).orElse(null);
            if (original != null) {
                String entityBranchId = original.getTable().getRoom().getBranch().getBranchId();
                BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
                branchAccessService.validateEntityBranch(entityBranchId, error);
                if (error.hasError()) return error.toResponse();
            }

            List<Long> ids = Arrays.stream(detailIds.split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            List<Long> sessions = orderService.splitBill(sessionId, ids);
            User user = getLoggedInUser();
            String branchId = (original != null) ? original.getTable().getRoom().getBranch().getBranchId() : getActiveBranchId();
            authService.logAudit(user, "BILL_SPLIT", "Order", sessionId.toString(),
                "T?ch h?a ??n c?a b?n " + (original != null ? original.getTable().getName() : sessionId) + " (T?o phi?n b?n m?i #" + sessions.get(1) + ")",
                "127.0.0.1", branchId);
            KdsWebSocketHandler.broadcast("ORDER_STATE_CHANGED");
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/checkout/vnpay")
    public ResponseEntity<?> requestVNPayQR(@RequestParam Long sessionId) {
        try {
            TableSession sess = tableSessionRepository.findById(sessionId).orElse(null);
            if (sess != null) {
                String entityBranchId = sess.getTable().getRoom().getBranch().getBranchId();
                BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
                branchAccessService.validateEntityBranch(entityBranchId, error);
                if (error.hasError()) return error.toResponse();
            }

            String payData = orderService.generateVNPayQR(sessionId);
            return ResponseEntity.ok(Map.of("qrData", payData));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/checkout/payos")
    public ResponseEntity<?> requestPayOSPayment(@RequestParam Long sessionId) {
        try {
            TableSession sess = tableSessionRepository.findById(sessionId).orElse(null);
            if (sess != null) {
                String entityBranchId = sess.getTable().getRoom().getBranch().getBranchId();
                BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
                branchAccessService.validateEntityBranch(entityBranchId, error);
                if (error.hasError()) return error.toResponse();
            }

            double finalAmount = orderService.getFinalAmount(sessionId);
            long orderCode = PayOSHelper.generateOrderCode();

            if (sess != null) {
                sess.setOrderCode(orderCode);
                tableSessionRepository.save(sess);
            }

            String returnUrl = "http://localhost:3000/pos?status=success&session=" + sessionId;
            String cancelUrl = "http://localhost:3000/pos?status=cancel&session=" + sessionId;

            String checkoutUrl = "";
            try {
                Map<String, Object> payosData = payOSHelper.createPaymentLink(
                        orderCode,
                        finalAmount,
                        "RMSPOS" + sessionId,
                        returnUrl,
                        cancelUrl
                );

                if (payosData != null && payosData.containsKey("checkoutUrl")) {
                    checkoutUrl = (String) payosData.get("checkoutUrl");
                }
            } catch (Exception e) {
                log.warn("[PAYOS POS] Failed to create real PayOS payment link: {}. Falling back to mock payment portal.", e.getMessage());
                checkoutUrl = "http://localhost:8080/api/public/payos/mock-checkout"
                        + "?orderCode=" + orderCode
                        + "&amount=" + finalAmount
                        + "&description=" + "RMSPOS" + sessionId
                        + "&returnUrl=" + java.net.URLEncoder.encode(returnUrl, java.nio.charset.StandardCharsets.UTF_8)
                        + "&cancelUrl=" + java.net.URLEncoder.encode(cancelUrl, java.nio.charset.StandardCharsets.UTF_8);
            }

            if (sess != null && !checkoutUrl.isEmpty()) {
                sess.setCheckoutUrl(checkoutUrl);
                tableSessionRepository.save(sess);
            }

            return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl, "orderCode", orderCode));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/checkout/confirm")
    public ResponseEntity<?> finalizePayment(@RequestParam Long sessionId, @RequestParam double amount, @RequestParam(required = false, defaultValue = "CASH") String paymentMethod) {
        try {
            TableSession session = tableSessionRepository.findById(sessionId).orElse(null);
            if (session != null) {
                String entityBranchId = session.getTable().getRoom().getBranch().getBranchId();
                BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
                branchAccessService.validateEntityBranch(entityBranchId, error);
                if (error.hasError()) return error.toResponse();
            }

            orderService.confirmPayment(sessionId, amount, paymentMethod);
            User user = getLoggedInUser();
            String branchId = (session != null) ? session.getTable().getRoom().getBranch().getBranchId() : getActiveBranchId();
            authService.logAudit(user, "BILL_PAYMENT", "Order", sessionId.toString(),
                "Thanh to?n th?nh c?ng h?a ??n b?n " + (session != null ? session.getTable().getName() : sessionId) + ", s? ti?n: ?" + String.format("%,.0f", amount) + " (" + paymentMethod + ")",
                "127.0.0.1", branchId);
            KdsWebSocketHandler.broadcast("ORDER_STATE_CHANGED");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/pos/order-logs/summary")
    public ResponseEntity<?> getOrderLogsSummary(@RequestParam(required = false, defaultValue = "day") String range) {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            LocalDateTime start;
            if ("week".equalsIgnoreCase(range)) {
                start = LocalDate.now().minusDays(7).atStartOfDay();
            } else if ("month".equalsIgnoreCase(range)) {
                start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            } else {
                start = LocalDate.now().atStartOfDay();
            }

            List<Order> orders = orderRepository.findByBranchId(branchId);
            List<Order> todayOrders = orders.stream()
                    .filter(o -> o.getOrderDate() != null && o.getOrderDate().isAfter(start))
                    .filter(o -> "SERVED".equalsIgnoreCase(o.getStatus()))
                    .collect(Collectors.toList());

            double totalRevenue = todayOrders.stream().mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0).sum();
            int totalOrders = todayOrders.size();
            double avgOrder = totalOrders > 0 ? totalRevenue / totalOrders : 0.0;

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalRevenue", totalRevenue);
            summary.put("totalOrders", totalOrders);
            summary.put("averageOrderValue", avgOrder);
            summary.put("topSellingItem", "N/A");
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/pos/order-logs")
    public ResponseEntity<?> getOrderLogs(@RequestParam(required = false, defaultValue = "day") String range) {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            LocalDateTime start;
            if ("week".equalsIgnoreCase(range)) {
                start = LocalDate.now().minusDays(7).atStartOfDay();
            } else if ("month".equalsIgnoreCase(range)) {
                start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            } else {
                start = LocalDate.now().atStartOfDay();
            }

            List<Order> orders = orderRepository.findByBranchId(branchId);
            List<Map<String, Object>> result = orders.stream()
                    .filter(o -> o.getOrderDate() != null && o.getOrderDate().isAfter(start))
                    .sorted((a, b) -> b.getOrderDate().compareTo(a.getOrderDate()))
                    .map(o -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", o.getId());
                        map.put("orderNumber", "#" + o.getId());
                        map.put("totalAmount", o.getTotalAmount());
                        map.put("status", o.getStatus());
                        map.put("paymentMethod", "CASH");
                        map.put("createdAt", o.getOrderDate() != null ? o.getOrderDate().toString() : "");
                        map.put("createdBy", o.getSession() != null && o.getSession().getTable() != null ? o.getSession().getTable().getName() : "");
                        return map;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/pos/bank-setting")
    public ResponseEntity<?> getBankSetting() {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            Optional<BankSetting> settingOpt = bankSettingRepository.findByBranchBranchId(branchId);
            if (settingOpt.isPresent()) {
                return ResponseEntity.ok(settingOpt.get());
            }
            
            // Fallback to Tenant bank account details
            User loggedInUser = getLoggedInUser();
            if (loggedInUser != null && loggedInUser.getTenant() != null) {
                web.restaurant.swp.modules.tenant.model.Tenant tenant = loggedInUser.getTenant();
                Map<String, String> data = new HashMap<>();
                data.put("bankName", tenant.getBankName() != null ? tenant.getBankName() : "");
                data.put("bankCode", tenant.getBankBranch() != null ? tenant.getBankBranch() : "");
                data.put("accountNumber", tenant.getBankAccountNo() != null ? tenant.getBankAccountNo() : "");
                data.put("accountHolder", tenant.getBankAccountName() != null ? tenant.getBankAccountName() : "");
                return ResponseEntity.ok(data);
            }
            
            return ResponseEntity.ok(new HashMap<>());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/bank-setting")
    public ResponseEntity<?> saveBankSetting(@RequestParam String bankName, @RequestParam String bankCode, @RequestParam String accountNumber, @RequestParam String accountHolder) {
        try {
            User loggedInUser = branchAccessService.getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> 
                "ADMIN".equalsIgnoreCase(r.getName()) ||
                "COOPERATOR".equalsIgnoreCase(r.getName()) ||
                "MANAGER".equalsIgnoreCase(r.getName())
            )) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác này.");
            }

            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new RuntimeException("Kh?ng t?m th?y chi nh?nh"));

            Optional<BankSetting> settingOpt = bankSettingRepository.findByBranchBranchId(branchId);
            BankSetting setting;
            if (settingOpt.isEmpty()) {
                setting = new BankSetting();
                setting.setBranch(branch);
            } else {
                setting = settingOpt.get();
            }

            setting.setBankName(bankName);
            setting.setBankCode(bankCode);
            setting.setAccountNumber(accountNumber);
            setting.setAccountHolder(accountHolder);

            setting = bankSettingRepository.save(setting);
            return ResponseEntity.ok(setting);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/pos/products")
    public ResponseEntity<?> getProducts() {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            String tenantId = getActiveTenantId();
            List<Product> products = productRepository.findByTenantTenantIdAndIsActiveTrue(tenantId);

            List<Map<String, Object>> result = new ArrayList<>();
            for (Product product : products) {
                Map<String, Object> pMap = new HashMap<>();
                pMap.put("id", product.getId());
                pMap.put("name", product.getName());
                pMap.put("description", product.getDescription());
                pMap.put("isActive", product.isActive());

                if (product.getCategory() != null) {
                    Map<String, Object> catMap = new HashMap<>();
                    catMap.put("id", product.getCategory().getId());
                    catMap.put("name", product.getCategory().getName());
                    pMap.put("category", catMap);
                }

                List<ProductVariant> variants = productVariantRepository.findByProductId(product.getId());
                List<Map<String, Object>> variantList = new ArrayList<>();
                for (ProductVariant v : variants) {
                    Map<String, Object> vMap = new HashMap<>();
                    vMap.put("id", v.getId());
                    vMap.put("name", v.getName());
                    vMap.put("price", v.getPrice());
                    variantList.add(vMap);
                }
                pMap.put("variants", variantList);
                result.add(pMap);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/pos/rooms")
    public ResponseEntity<?> getRooms() {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            List<Room> rooms = roomRepository.findByBranchBranchIdOrderByDisplayOrderAscIdAsc(branchId);
            return ResponseEntity.ok(rooms);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ──── Floor Plan for POS ─────────────────────────────────────────

    @GetMapping("/api/pos/floor-plans/active")
    public ResponseEntity<?> getActiveFloorPlan(@RequestParam(required = false) Long roomId,
                                                 @RequestParam(required = false) String branchId,
                                                 @RequestParam(required = false) Integer floorNumber) {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String resolvedBranchId = branchAccessService.validateAndGetBranchId(branchId, error);
            if (error.hasError()) return error.toResponse();

            FloorPlan plan = null;

            if (roomId != null) {
                Room room = roomRepository.findById(roomId).orElse(null);
                if (room != null && room.getBranch().getBranchId().equals(resolvedBranchId)) {
                    plan = floorPlanRepository.findByRoom_IdAndStatusOrderByIdAsc(roomId, "published")
                            .stream()
                            .findFirst()
                            .orElse(null);
                }
            } else if (floorNumber != null) {
                List<FloorPlan> plans = floorPlanRepository
                        .findByBranch_BranchIdAndStatusOrderByFloorNumberAsc(resolvedBranchId, "published");
                for (FloorPlan fp : plans) {
                    if (fp.getFloorNumber().equals(floorNumber)) {
                        plan = fp;
                        break;
                    }
                }
            } else {
                List<FloorPlan> plans = floorPlanRepository
                        .findByBranch_BranchIdAndStatusOrderByFloorNumberAsc(resolvedBranchId, "published");
                if (!plans.isEmpty()) {
                    plan = plans.get(0);
                }
            }

            if (plan == null) {
                return ResponseEntity.ok(null);
            }

            List<FloorPlanObject> objects = floorPlanObjectRepository
                    .findByFloorPlanIdOrdered(plan.getId());

            // Build response with linked POS table data
            List<Map<String, Object>> objectList = new ArrayList<>();
            for (FloorPlanObject obj : objects) {
                Map<String, Object> objMap = new LinkedHashMap<>();
                objMap.put("id", obj.getId());
                objMap.put("tableId", obj.getTableId());
                objMap.put("objectType", obj.getObjectType());
                objMap.put("label", obj.getLabel());
                objMap.put("x", obj.getX());
                objMap.put("y", obj.getY());
                objMap.put("width", obj.getWidth());
                objMap.put("height", obj.getHeight());
                objMap.put("rotation", obj.getRotation());
                objMap.put("shape", obj.getShape());
                objMap.put("zIndex", obj.getZIndex());
                objMap.put("styleJson", obj.getStyleJson());
                objMap.put("metadataJson", obj.getMetadataJson());
                objMap.put("isVisible", obj.getIsVisible());
                objMap.put("isLocked", obj.getIsLocked());

                // If table object, fetch linked POS table data
                if ("table".equalsIgnoreCase(obj.getObjectType()) && (obj.getTableId() != null || obj.getMetadataJson() != null)) {
                    try {
                        Map<String, Object> meta = obj.getMetadataJson() != null ? obj.getMetadataJson() : Map.of();
                        Object linkedTableId = obj.getTableId() != null ? obj.getTableId() : meta.get("linkedTableId");
                        if (linkedTableId == null) linkedTableId = meta.get("tableEntityId");
                        if (linkedTableId == null) linkedTableId = meta.get("tableId");
                        if (linkedTableId != null) {
                            Long tableId = linkedTableId instanceof Number
                                    ? ((Number) linkedTableId).longValue()
                                    : Long.parseLong(linkedTableId.toString());
                            Optional<TableEntity> tableOpt = tableRepository.findById(tableId);
                            if (tableOpt.isPresent()) {
                                TableEntity t = tableOpt.get();
                                Map<String, Object> posTable = new LinkedHashMap<>();
                                posTable.put("id", t.getId());
                                posTable.put("name", t.getName());
                                posTable.put("status", t.getStatus());
                                posTable.put("capacity", t.getCapacity());
                                posTable.put("tableStyle", t.getTableStyle());
                                posTable.put("shape", t.getShape());
                                // Find active session
                                Optional<TableSession> sessionOpt = tableSessionRepository
                                        .findByTableIdAndStatus(t.getId(), "ACTIVE");
                                posTable.put("activeSessionId", sessionOpt.map(TableSession::getId).orElse(null));
                                objMap.put("posTable", posTable);
                                objMap.put("linked", true);
                            } else {
                                objMap.put("linked", false);
                            }
                        } else {
                            objMap.put("linked", false);
                        }
                    } catch (Exception e) {
                        objMap.put("linked", false);
                    }
                } else {
                    objMap.put("linked", false);
                }

                objectList.add(objMap);
            }

            // Build floor plan response
            Map<String, Object> planMap = new LinkedHashMap<>();
            planMap.put("id", plan.getId());
            planMap.put("name", plan.getName());
            planMap.put("floorNumber", plan.getFloorNumber());
            planMap.put("roomId", plan.getRoom() != null ? plan.getRoom().getId() : null);
            planMap.put("room", plan.getRoom() != null
                    ? Map.of("id", plan.getRoom().getId(), "name", plan.getRoom().getName())
                    : null);
            planMap.put("width", plan.getWidth());
            planMap.put("height", plan.getHeight());
            planMap.put("backgroundMode", plan.getBackgroundMode());
            planMap.put("floorDiagramImageUrl", plan.getFloorDiagramImageUrl());
            planMap.put("floorDiagramImageKey", plan.getFloorDiagramImageKey());
            planMap.put("floorDiagramFitMode", plan.getFloorDiagramFitMode());
            planMap.put("floorDiagramX", plan.getFloorDiagramX());
            planMap.put("floorDiagramY", plan.getFloorDiagramY());
            planMap.put("floorDiagramWidth", plan.getFloorDiagramWidth());
            planMap.put("floorDiagramHeight", plan.getFloorDiagramHeight());
            planMap.put("floorDiagramScale", plan.getFloorDiagramScale());
            planMap.put("floorDiagramRotation", plan.getFloorDiagramRotation());
            planMap.put("panoramaUrl", plan.getPanoramaUrl());
            planMap.put("panoramaKey", plan.getPanoramaKey());
            planMap.put("panoramaType", plan.getPanoramaType());
            planMap.put("status", plan.getStatus());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("floorPlan", planMap);
            response.put("objects", objectList);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private List<Map<String, Object>> enrichTables(List<TableEntity> list) {
        List<Map<String, Object>> response = new ArrayList<>();
        for (TableEntity t : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getId());
            map.put("name", t.getName());
            map.put("capacity", t.getCapacity());
            map.put("status", t.getStatus());
            map.put("guestCount", t.getGuestCount());
            map.put("tableStyle", t.getTableStyle());
            map.put("shape", t.getShape());
            if (t.getRoom() != null) {
                map.put("room", Map.of("id", t.getRoom().getId(), "name", t.getRoom().getName()));
            } else {
                map.put("room", null);
            }

            Optional<TableSession> sessionOpt = tableSessionRepository.findByTableIdAndStatus(t.getId(), "ACTIVE");
            if (sessionOpt.isPresent()) {
                TableSession session = sessionOpt.get();
                map.put("activeSessionId", session.getId());
                map.put("sessionOpenedAt", session.getCheckInTime() != null ? session.getCheckInTime().toString() : null);

                List<Order> orders = orderRepository.findBySessionId(session.getId());
                double total = 0.0;
                for (Order o : orders) {
                    String status = o.getStatus();
                    if ("PENDING".equalsIgnoreCase(status) || "SENT".equalsIgnoreCase(status)
                            || "COOKING".equalsIgnoreCase(status) || "READY".equalsIgnoreCase(status)
                            || "SERVED".equalsIgnoreCase(status)) {
                        total += o.getTotalAmount() != null ? o.getTotalAmount() : 0.0;
                    }
                }
                map.put("sessionTotalAmount", total);
            } else {
                map.put("activeSessionId", null);
                map.put("sessionOpenedAt", null);
                map.put("sessionTotalAmount", 0.0);
            }
            response.add(map);
        }
        return response;
    }

    @GetMapping("/api/pos/tables")
    public ResponseEntity<?> getTables(@RequestParam(required = false) Long roomId) {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            if (roomId != null) {
                Room room = roomRepository.findById(roomId)
                        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy dữ liệu"));
                if (!room.getBranch().getBranchId().equals(branchId)) {
                    return message(403, "You do not have access to this branch");
                }
                return ResponseEntity.ok(enrichTables(tableRepository.findByRoomIdOrderByIdAsc(roomId)));
            }

            return ResponseEntity.ok(enrichTables(tableRepository.findByRoomBranchBranchIdOrderByRoomDisplayOrderAscIdAsc(branchId)));
        } catch (NoSuchElementException e) {
            return message(404, "Không tìm thấy dữ liệu");
        } catch (Exception e) {
            return message(400, e.getMessage());
        }
    }

    @PostMapping("/api/pos/rooms/add")
    public ResponseEntity<?> addRoom(
            @RequestParam String name,
            @RequestParam(required = false) String floorPlanImageUrl,
            @RequestParam(required = false) String panoramaUrl,
            @RequestParam(required = false) String panoramaType,
            @RequestParam(required = false, defaultValue = "0") Integer displayOrder,
            @RequestParam(required = false) Integer floorPlanWidth,
            @RequestParam(required = false) Integer floorPlanHeight) {
        try {
            if (!canManageRoomsAndTables(getLoggedInUser())) {
                return message(403, "Không có quyền thực hiện thao tác này.");
            }

            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            String normalizedName = normalizeRequiredName(name, "Tên phòng là bắt buộc.");
            if (roomRepository.existsByBranchBranchIdAndNameIgnoreCase(branchId, normalizedName)) {
                return message(409, "Tên phòng đã tồn tại trong chi nhánh này.");
            }

            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy dữ liệu"));
            Room room = Room.builder()
                    .name(normalizedName)
                    .branch(branch)
                    .floorPlanImageUrl(floorPlanImageUrl)
                    .panoramaUrl(panoramaUrl)
                    .panoramaType(panoramaType)
                    .displayOrder(displayOrder)
                    .floorPlanWidth(floorPlanWidth)
                    .floorPlanHeight(floorPlanHeight)
                    .build();
            return ResponseEntity.ok(roomRepository.save(room));
        } catch (IllegalArgumentException e) {
            return message(400, e.getMessage());
        } catch (NoSuchElementException e) {
            return message(404, "Không tìm thấy dữ liệu");
        } catch (Exception e) {
            return message(500, e.getMessage());
        }
    }

    @PostMapping("/api/pos/rooms/update")
    public ResponseEntity<?> updateRoom(
            @RequestParam Long roomId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String floorPlanImageUrl,
            @RequestParam(required = false) String panoramaUrl,
            @RequestParam(required = false) String panoramaType,
            @RequestParam(required = false) Integer displayOrder,
            @RequestParam(required = false) Integer floorPlanWidth,
            @RequestParam(required = false) Integer floorPlanHeight) {
        try {
            if (!canManageRoomsAndTables(getLoggedInUser())) {
                return message(403, "Không có quyền thực hiện thao tác này.");
            }

            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy dữ liệu"));

            String entityBranchId = room.getBranch().getBranchId();
            BranchAccessService.ErrorHolder branchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(entityBranchId, branchError);
            if (branchError.hasError()) return branchError.toResponse();

            if (name != null) {
                String normalizedName = normalizeRequiredName(name, "Tên phòng là bắt buộc.");
                if (roomRepository.existsByBranchBranchIdAndNameIgnoreCaseAndIdNot(entityBranchId, normalizedName, roomId)) {
                    return message(409, "Tên phòng đã tồn tại trong chi nhánh này.");
                }
                room.setName(normalizedName);
            }
            if (floorPlanImageUrl != null) room.setFloorPlanImageUrl(floorPlanImageUrl);
            if (panoramaUrl != null) room.setPanoramaUrl(panoramaUrl);
            if (panoramaType != null) room.setPanoramaType(panoramaType);
            if (displayOrder != null) room.setDisplayOrder(displayOrder);
            if (floorPlanWidth != null) room.setFloorPlanWidth(floorPlanWidth);
            if (floorPlanHeight != null) room.setFloorPlanHeight(floorPlanHeight);

            return ResponseEntity.ok(roomRepository.save(room));
        } catch (IllegalArgumentException e) {
            return message(400, e.getMessage());
        } catch (NoSuchElementException e) {
            return message(404, "Không tìm thấy dữ liệu");
        } catch (Exception e) {
            return message(500, e.getMessage());
        }
    }

    @PostMapping("/api/pos/rooms/delete")
    public ResponseEntity<?> deleteRoom(@RequestParam Long roomId) {
        try {
            if (!canManageRoomsAndTables(getLoggedInUser())) {
                return message(403, "Không có quyền thực hiện thao tác này.");
            }

            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy dữ liệu"));

            String entityBranchId = room.getBranch().getBranchId();
            BranchAccessService.ErrorHolder branchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(entityBranchId, branchError);
            if (branchError.hasError()) return branchError.toResponse();

            if (!tableRepository.findByRoomId(roomId).isEmpty()) {
                return message(409, "Không thể xóa phòng vì vẫn còn bàn trong phòng này.");
            }
            if (floorPlanRepository.existsByRoom_Id(roomId)) {
                return message(409, "Không thể xóa phòng vì đang được sử dụng trong sơ đồ tầng.");
            }

            roomRepository.delete(room);
            return ResponseEntity.ok().build();
        } catch (NoSuchElementException e) {
            return message(404, "Không tìm thấy dữ liệu");
        } catch (Exception e) {
            return message(500, e.getMessage());
        }
    }

    @PostMapping("/api/pos/tables/add")
    public ResponseEntity<?> addTable(
            @RequestParam String name,
            @RequestParam Long roomId,
            @RequestParam Integer capacity,
            @RequestParam(required = false) String tableStyle,
            @RequestParam(required = false) Double layoutX,
            @RequestParam(required = false) Double layoutY,
            @RequestParam(required = false) Double layoutWidth,
            @RequestParam(required = false) Double layoutHeight,
            @RequestParam(required = false) Double layoutRotation,
            @RequestParam(required = false) Double layoutRadius,
            @RequestParam(required = false) String displayLabel) {
        try {
            if (!canManageRoomsAndTables(getLoggedInUser())) {
                return message(403, "Không có quyền thực hiện thao tác này.");
            }

            String normalizedName = normalizeRequiredName(name, "Tên bàn là bắt buộc.");
            if (capacity == null || capacity < 1) {
                return message(400, "Sức chứa phải lớn hơn hoặc bằng 1.");
            }
            String normalizedStyle = normalizeTableStyle(tableStyle);

            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy dữ liệu"));

            BranchAccessService.ErrorHolder branchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(room.getBranch().getBranchId(), branchError);
            if (branchError.hasError()) return branchError.toResponse();

            if (tableRepository.existsByRoomIdAndNameIgnoreCase(roomId, normalizedName)) {
                return message(409, "Tên bàn đã tồn tại trong phòng này.");
            }

            TableEntity table = TableEntity.builder()
                    .name(normalizedName)
                    .room(room)
                    .capacity(capacity)
                    .status("EMPTY")
                    .guestCount(0)
                    .layoutX(layoutX)
                    .layoutY(layoutY)
                    .layoutWidth(layoutWidth)
                    .layoutHeight(layoutHeight)
                    .layoutRotation(layoutRotation)
                    .layoutRadius(layoutRadius)
                    .displayLabel(displayLabel)
                    .tableStyle(normalizedStyle)
                    .shape(shapeForTableStyle(normalizedStyle))
                    .build();
            return ResponseEntity.ok(tableRepository.save(table));
        } catch (IllegalArgumentException e) {
            return message(400, e.getMessage());
        } catch (NoSuchElementException e) {
            return message(404, "Không tìm thấy dữ liệu");
        } catch (Exception e) {
            return message(500, e.getMessage());
        }
    }

    @PostMapping("/api/pos/tables/update")
    @Transactional
    public ResponseEntity<?> updateTable(
            @RequestParam Long tableId,
            @RequestParam String name,
            @RequestParam Long roomId,
            @RequestParam Integer capacity,
            @RequestParam(required = false) String tableStyle,
            @RequestParam(required = false) Double layoutX,
            @RequestParam(required = false) Double layoutY,
            @RequestParam(required = false) Double layoutWidth,
            @RequestParam(required = false) Double layoutHeight,
            @RequestParam(required = false) Double layoutRotation,
            @RequestParam(required = false) Double layoutRadius,
            @RequestParam(required = false) String displayLabel) {
        try {
            if (!canManageRoomsAndTables(getLoggedInUser())) {
                return message(403, "Không có quyền thực hiện thao tác này.");
            }

            String normalizedName = normalizeRequiredName(name, "Tên bàn là bắt buộc.");
            if (capacity == null || capacity < 1) {
                return message(400, "Sức chứa phải lớn hơn hoặc bằng 1.");
            }
            String normalizedStyle = normalizeTableStyle(tableStyle);

            TableEntity table = tableRepository.findById(tableId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy dữ liệu"));
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy dữ liệu"));
            Long previousRoomId = table.getRoom() != null ? table.getRoom().getId() : null;

            BranchAccessService.ErrorHolder tableBranchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(table.getRoom().getBranch().getBranchId(), tableBranchError);
            if (tableBranchError.hasError()) return tableBranchError.toResponse();

            BranchAccessService.ErrorHolder roomBranchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(room.getBranch().getBranchId(), roomBranchError);
            if (roomBranchError.hasError()) return roomBranchError.toResponse();

            if (tableRepository.existsByRoomIdAndNameIgnoreCaseAndIdNot(roomId, normalizedName, tableId)) {
                return message(409, "Tên bàn đã tồn tại trong phòng này.");
            }

            table.setName(normalizedName);
            table.setRoom(room);
            table.setCapacity(capacity);
            table.setTableStyle(normalizedStyle);
            table.setShape(shapeForTableStyle(normalizedStyle));
            if (layoutX != null) table.setLayoutX(layoutX);
            if (layoutY != null) table.setLayoutY(layoutY);
            if (layoutWidth != null) table.setLayoutWidth(layoutWidth);
            if (layoutHeight != null) table.setLayoutHeight(layoutHeight);
            if (layoutRotation != null) table.setLayoutRotation(layoutRotation);
            if (layoutRadius != null) table.setLayoutRadius(layoutRadius);
            if (displayLabel != null) table.setDisplayLabel(displayLabel);

            table = tableRepository.save(table);
            if (previousRoomId != null && !previousRoomId.equals(roomId)) {
                floorPlanObjectRepository.deleteByTableIdOutsideRoom(tableId, roomId);
            }
            syncFloorPlanObjectsForTable(table);
            return ResponseEntity.ok(table);
        } catch (IllegalArgumentException e) {
            return message(400, e.getMessage());
        } catch (NoSuchElementException e) {
            return message(404, "Không tìm thấy dữ liệu");
        } catch (Exception e) {
            return message(500, e.getMessage());
        }
    }

    @PostMapping("/api/pos/tables/delete")
    @Transactional
    public ResponseEntity<?> deleteTable(@RequestParam Long tableId) {
        try {
            if (!canManageRoomsAndTables(getLoggedInUser())) {
                return message(403, "Không có quyền thực hiện thao tác này.");
            }

            TableEntity table = tableRepository.findById(tableId)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy dữ liệu"));

            BranchAccessService.ErrorHolder branchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(table.getRoom().getBranch().getBranchId(), branchError);
            if (branchError.hasError()) return branchError.toResponse();

            if (!"EMPTY".equalsIgnoreCase(table.getStatus())
                    || tableSessionRepository.findByTableIdAndStatus(tableId, "ACTIVE").isPresent()) {
                return message(409, "Không thể xóa bàn vì đang có phiên/order đang hoạt động.");
            }

            floorPlanObjectRepository.deleteByTableId(tableId);
            tableRepository.delete(table);
            return ResponseEntity.ok().build();
        } catch (NoSuchElementException e) {
            return message(404, "Không tìm thấy dữ liệu");
        } catch (Exception e) {
            return message(500, e.getMessage());
        }
    }

    @GetMapping("/api/pos/branch-admins")
    public ResponseEntity<?> getBranchAdmins() {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Kh?ng c? quy?n truy c?p.");
            }

            String tenantId = getActiveTenantId();
            boolean isPartnerAdmin = loggedInUser.getBranch() != null;
            List<User> users = userRepository.findAll().stream()
                    .filter(u -> u.getTenant() != null && u.getTenant().getTenantId().equals(tenantId))
                    .filter(u -> !u.getEmail().equalsIgnoreCase(loggedInUser.getEmail()))
                    .filter(u -> {
                        boolean isAdmin = u.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));
                        boolean isManager = u.getRoles().stream().anyMatch(r -> "MANAGER".equalsIgnoreCase(r.getName()));
                        if (isPartnerAdmin) {
                            return isManager;
                        } else {
                            return isAdmin || isManager;
                        }
                    })
                    .collect(Collectors.toList());

            List<Map<String, Object>> result = new ArrayList<>();
            for (User u : users) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", u.getId());
                map.put("name", u.getName());
                map.put("email", u.getEmail());
                map.put("branchId", u.getBranch() != null ? u.getBranch().getBranchId() : "");
                map.put("branchName", u.getBranch() != null ? u.getBranch().getName() : "H? Th?ng (Kh?ng chi nh?nh)");
                
                String roleName = u.getRoles().stream()
                        .map(Role::getName)
                        .filter(r -> "ADMIN".equalsIgnoreCase(r) || "MANAGER".equalsIgnoreCase(r))
                        .findFirst().orElse("MANAGER");
                map.put("roleName", roleName);
                map.put("isActive", u.isActive());
                result.add(map);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/branch-admins/add")
    public ResponseEntity<?> addBranchAdmin(
            @RequestParam String email, 
            @RequestParam String name, 
            @RequestParam String password, 
            @RequestParam(required = false) String branchId, 
            @RequestParam String roleName,
            @RequestParam(required = false) String newBranchId,
            @RequestParam(required = false) String newBranchName,
            @RequestParam(required = false) String newBranchAddress,
            @RequestParam(required = false) String newBranchPhone) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "COOPERATOR".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện.");
            }

            boolean isPartnerAdmin = loggedInUser.getBranch() != null;
            if (isPartnerAdmin) {
                if (branchId == null || branchId.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Vui l?ng ch?n chi nh?nh qu?n l?.");
                }
            }

            if (userRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body("Email ?? t?n t?i trong h? th?ng.");
            }

            Branch branch = null;
            if ("_NEW_".equals(branchId)) {
                if (newBranchId == null || newBranchId.trim().isEmpty() || 
                    newBranchName == null || newBranchName.trim().isEmpty() ||
                    newBranchAddress == null || newBranchAddress.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Vui l?ng nh?p ??y ?? m?, t?n v? ??a ch? chi nh?nh m?i.");
                }
                Optional<Branch> existingBranch = branchRepository.findById(newBranchId.trim());
                if (existingBranch.isPresent()) {
                    return ResponseEntity.badRequest().body("Mã chi nhánh đã tồn tại trong hệ thống. Vui lòng chọn mã khác.");
                } else {
                    branch = Branch.builder()
                            .branchId(newBranchId.trim())
                            .name(newBranchName.trim())
                            .address(newBranchAddress != null ? newBranchAddress.trim() : "")
                            .phone(newBranchPhone != null ? newBranchPhone.trim() : "")
                            .tenant(loggedInUser.getTenant())
                            .isActive(true)
                            .build();
                    branch = branchRepository.save(branch);
                }
            } else if (branchId != null && !branchId.trim().isEmpty()) {
                branch = branchRepository.findById(branchId).orElse(null);
            }

            boolean isCooperator = loggedInUser.getRoles().stream().anyMatch(r -> "COOPERATOR".equalsIgnoreCase(r.getName()));
            String resolvedRoleName = (isPartnerAdmin || isCooperator) ? "MANAGER" : "ADMIN";
            Role role = roleRepository.findByName(resolvedRoleName)
                    .orElseThrow(() -> new RuntimeException("Kh?ng t?m th?y vai tr? " + resolvedRoleName));

            User user = User.builder()
                    .email(email)
                    .name(name)
                    .password(passwordEncoder.encode(password))
                    .branch(branch)
                    .roles(new HashSet<>(Arrays.asList(role)))
                    .isActive(true)
                    .tenant(loggedInUser.getTenant())
                    .build();

            user = userRepository.save(user);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/branch-admins/update")
    public ResponseEntity<?> updateBranchAdmin(
            @RequestParam Long id, 
            @RequestParam String email, 
            @RequestParam String name, 
            @RequestParam(required = false) String password, 
            @RequestParam(required = false) String branchId, 
            @RequestParam String roleName, 
            @RequestParam boolean isActive,
            @RequestParam(required = false) String newBranchId,
            @RequestParam(required = false) String newBranchName,
            @RequestParam(required = false) String newBranchAddress,
            @RequestParam(required = false) String newBranchPhone) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "COOPERATOR".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện.");
            }

            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Kh?ng t?m th?y t?i kho?n."));

            boolean isPartnerAdmin = loggedInUser.getBranch() != null;
            if (isPartnerAdmin) {
                boolean targetIsManager = user.getRoles().stream().anyMatch(r -> "MANAGER".equalsIgnoreCase(r.getName()));
                if (!targetIsManager) {
                    return ResponseEntity.status(403).body("Kh?ng c? quy?n ch?nh s?a t?i kho?n qu?n tr? kh?c.");
                }
                if (branchId == null || branchId.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Vui l?ng ch?n chi nh?nh qu?n l?.");
                }
            }

            if (user.getTenant() == null || !user.getTenant().getTenantId().equals(loggedInUser.getTenant().getTenantId())) {
                return ResponseEntity.status(403).body("Kh?ng c? quy?n th?c hi?n thao t?c tr?n t?i kho?n thu?c tenant kh?c.");
            }

            if (!user.getEmail().equalsIgnoreCase(email)) {
                if (userRepository.findByEmail(email).isPresent()) {
                    return ResponseEntity.badRequest().body("Email ?? t?n t?i.");
                }
                user.setEmail(email);
            }

            user.setName(name);
            user.setActive(isActive);

            if (password != null && !password.trim().isEmpty()) {
                user.setPassword(passwordEncoder.encode(password));
            }

            Branch branch = null;
            if ("_NEW_".equals(branchId)) {
                if (newBranchId == null || newBranchId.trim().isEmpty() || 
                    newBranchName == null || newBranchName.trim().isEmpty() ||
                    newBranchAddress == null || newBranchAddress.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Vui l?ng nh?p ??y ?? m?, t?n v? ??a ch? chi nh?nh m?i.");
                }
                Optional<Branch> existingBranch = branchRepository.findById(newBranchId.trim());
                if (existingBranch.isPresent()) {
                    return ResponseEntity.badRequest().body("Mã chi nhánh đã tồn tại trong hệ thống. Vui lòng chọn mã khác.");
                } else {
                    branch = Branch.builder()
                            .branchId(newBranchId.trim())
                            .name(newBranchName.trim())
                            .address(newBranchAddress != null ? newBranchAddress.trim() : "")
                            .phone(newBranchPhone != null ? newBranchPhone.trim() : "")
                            .tenant(loggedInUser.getTenant())
                            .isActive(true)
                            .build();
                    branch = branchRepository.save(branch);
                }
            } else if (branchId != null && !branchId.trim().isEmpty()) {
                branch = branchRepository.findById(branchId).orElse(null);
            }
            user.setBranch(branch);

            boolean isCooperator = loggedInUser.getRoles().stream().anyMatch(r -> "COOPERATOR".equalsIgnoreCase(r.getName()));
            String resolvedRoleName = (isPartnerAdmin || isCooperator) ? "MANAGER" : "ADMIN";
            Role role = roleRepository.findByName(resolvedRoleName)
                    .orElseThrow(() -> new RuntimeException("Kh?ng t?m th?y vai tr?."));
            user.setRoles(new HashSet<>(Arrays.asList(role)));

            user = userRepository.save(user);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/branch-admins/delete")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteBranchAdmin(@RequestParam Long id) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "COOPERATOR".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện.");
            }

            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Kh?ng t?m th?y t?i kho?n."));

            boolean isPartnerAdmin = loggedInUser.getBranch() != null;
            if (isPartnerAdmin) {
                boolean targetIsManager = user.getRoles().stream().anyMatch(r -> "MANAGER".equalsIgnoreCase(r.getName()));
                if (!targetIsManager) {
                    return ResponseEntity.status(403).body("Kh?ng c? quy?n x?a t?i kho?n qu?n tr? kh?c.");
                }
            }

            if (user.getTenant() == null || !user.getTenant().getTenantId().equals(loggedInUser.getTenant().getTenantId())) {
                return ResponseEntity.status(403).body("Kh?ng c? quy?n th?c hi?n thao t?c tr?n t?i kho?n thu?c tenant kh?c.");
            }

            Optional<Employee> empOpt = employeeRepository.findByUserId(id);
            if (empOpt.isPresent()) {
                user.setActive(false);
                userRepository.save(user);
                return ResponseEntity.ok(Map.of("message", "T?i kho?n c? h? s? nh?n s? li?n k?t. ?? v? hi?u h?a (kh?a) t?i kho?n thay v? x?a v?t l?.", "softDeleted", true));
            }

            userSessionRepository.deleteByUserId(id);
            userRepository.delete(user);
            return ResponseEntity.ok(Map.of("message", "?? x?a t?i kho?n th?nh c?ng.", "softDeleted", false));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
