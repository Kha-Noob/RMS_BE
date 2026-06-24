package web.restaurant.swp.modules.pos.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
            if ("PENDING".equalsIgnoreCase(order.getStatus()) || "SENT".equalsIgnoreCase(order.getStatus())) {
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
                    .orElseThrow(() -> new RuntimeException("Table not found"));
            String entityBranchId = table.getRoom().getBranch().getBranchId();
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(entityBranchId, error);
            if (error.hasError()) return error.toResponse();

            TableSession session = orderService.openTableSession(tableId, customerId);
            User user = getLoggedInUser();
            authService.logAudit(user, "OPEN_SESSION", "Order", session.getId().toString(),
                "Mở ca (Check-in) cho bàn " + session.getTable().getName(), "127.0.0.1", session.getTable().getRoom().getBranch().getBranchId());
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/order/add")
    public ResponseEntity<?> addToCart(@RequestParam Long sessionId, @RequestParam Long variantId, @RequestParam int quantity, @RequestParam(required = false, defaultValue = "") String notes) {
        try {
            TableSession sess = tableSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            String entityBranchId = sess.getTable().getRoom().getBranch().getBranchId();
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(entityBranchId, error);
            if (error.hasError()) return error.toResponse();

            OrderDetail detail = orderService.addItemToSession(sessionId, variantId, quantity, notes);
            User user = getLoggedInUser();
            authService.logAudit(user, "ORDER_ADD_ITEM", "Order", detail.getOrder().getId().toString(),
                "Thêm món: " + quantity + "x " + detail.getVariant().getProduct().getName() + " (" + detail.getVariant().getName() + ") vào bàn " + detail.getOrder().getSession().getTable().getName(),
                "127.0.0.1", detail.getOrder().getBranchId());
            return ResponseEntity.ok(detail);
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
                "Gửi yêu cầu chế biến món ăn bàn " + (session != null ? session.getTable().getName() : sessionId) + " xuống bếp",
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
                "Ghép hóa đơn từ bàn " + (src != null ? src.getTable().getName() : sourceSessionId) + " sang bàn " + (tgt != null ? tgt.getTable().getName() : targetSessionId),
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
                "Tách hóa đơn của bàn " + (original != null ? original.getTable().getName() : sessionId) + " (Tạo phiên bàn mới #" + sessions.get(1) + ")",
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
                "Thanh toán thành công hóa đơn bàn " + (session != null ? session.getTable().getName() : sessionId) + ", số tiền: ₫" + String.format("%,.0f", amount) + " (" + paymentMethod + ")",
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
            if (settingOpt.isEmpty()) {
                settingOpt = bankSettingRepository.findByBranchBranchId("01-2thang9");
            }
            if (settingOpt.isEmpty()) {
                return ResponseEntity.ok(new HashMap<>());
            }
            return ResponseEntity.ok(settingOpt.get());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/bank-setting")
    public ResponseEntity<?> saveBankSetting(@RequestParam String bankName, @RequestParam String bankCode, @RequestParam String accountNumber, @RequestParam String accountHolder) {
        try {
            User loggedInUser = branchAccessService.getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác này.");
            }

            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy chi nhánh"));

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
                    vMap.put("product", pMap);
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

            List<Room> rooms = roomRepository.findByBranchBranchId(branchId);
            return ResponseEntity.ok(rooms);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/pos/tables")
    public ResponseEntity<?> getTables() {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            List<TableEntity> tables = tableRepository.findByRoomBranchBranchId(branchId);
            return ResponseEntity.ok(tables);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/rooms/add")
    public ResponseEntity<?> addRoom(@RequestParam String name) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác này.");
            }
            
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy chi nhánh"));
            Room room = Room.builder()
                    .name(name)
                    .branch(branch)
                    .build();
            room = roomRepository.save(room);
            return ResponseEntity.ok(room);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/rooms/delete")
    public ResponseEntity<?> deleteRoom(@RequestParam Long roomId) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác này.");
            }
            
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng/khu vực"));

            String entityBranchId = room.getBranch().getBranchId();
            BranchAccessService.ErrorHolder branchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(entityBranchId, branchError);
            if (branchError.hasError()) return branchError.toResponse();

            List<TableEntity> tables = tableRepository.findByRoomId(roomId);
            if (!tables.isEmpty()) {
                return ResponseEntity.badRequest().body("Không thể xóa khu vực này vì vẫn còn bàn thuộc khu vực.");
            }
            
            roomRepository.delete(room);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/tables/add")
    public ResponseEntity<?> addTable(@RequestParam String name, @RequestParam Long roomId, @RequestParam Integer capacity) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác này.");
            }
            
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng/khu vực"));

            String entityBranchId = room.getBranch().getBranchId();
            BranchAccessService.ErrorHolder branchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(entityBranchId, branchError);
            if (branchError.hasError()) return branchError.toResponse();

            TableEntity table = TableEntity.builder()
                    .name(name)
                    .room(room)
                    .capacity(capacity)
                    .status("EMPTY")
                    .guestCount(0)
                    .build();
            table = tableRepository.save(table);
            return ResponseEntity.ok(table);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/tables/update")
    public ResponseEntity<?> updateTable(@RequestParam Long tableId, @RequestParam String name, @RequestParam Long roomId, @RequestParam Integer capacity) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác này.");
            }
            
            TableEntity table = tableRepository.findById(tableId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bàn"));
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng/khu vực"));
            
            table.setName(name);
            table.setRoom(room);
            table.setCapacity(capacity);
            table = tableRepository.save(table);
            return ResponseEntity.ok(table);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/pos/tables/delete")
    public ResponseEntity<?> deleteTable(@RequestParam Long tableId) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác này.");
            }
            
            TableEntity table = tableRepository.findById(tableId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bàn"));

            String entityBranchId = table.getRoom().getBranch().getBranchId();
            BranchAccessService.ErrorHolder branchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(entityBranchId, branchError);
            if (branchError.hasError()) return branchError.toResponse();

            if (!"EMPTY".equalsIgnoreCase(table.getStatus())) {
                return ResponseEntity.badRequest().body("Không thể xóa bàn đang có khách hoặc đã đặt.");
            }
            
            Optional<TableSession> sessionOpt = tableSessionRepository.findByTableIdAndStatus(tableId, "ACTIVE");
            if (sessionOpt.isPresent()) {
                return ResponseEntity.badRequest().body("Không thể xóa bàn đang có phiên hoạt động.");
            }
            
            boolean hasHistory = tableSessionRepository.existsByTableId(tableId);
            if (hasHistory) {
                return ResponseEntity.badRequest().body("Không thể xóa bàn này vì đã có lịch sử hoạt động/hóa đơn. Bạn có thể đổi tên bàn hoặc chuyển nó sang phòng khác.");
            }
            
            tableRepository.delete(table);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/pos/branch-admins")
    public ResponseEntity<?> getBranchAdmins() {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền truy cập.");
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
                map.put("branchName", u.getBranch() != null ? u.getBranch().getName() : "Hệ Thống (Không chi nhánh)");
                
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
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện.");
            }

            boolean isPartnerAdmin = loggedInUser.getBranch() != null;
            if (isPartnerAdmin) {
                if (branchId == null || branchId.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Vui lòng chọn chi nhánh quản lý.");
                }
            }

            if (userRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body("Email đã tồn tại trong hệ thống.");
            }

            Branch branch = null;
            if ("_NEW_".equals(branchId)) {
                if (newBranchId == null || newBranchId.trim().isEmpty() || 
                    newBranchName == null || newBranchName.trim().isEmpty() ||
                    newBranchAddress == null || newBranchAddress.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Vui lòng nhập đầy đủ mã, tên và địa chỉ chi nhánh mới.");
                }
                Optional<Branch> existingBranch = branchRepository.findById(newBranchId.trim());
                if (existingBranch.isPresent()) {
                    branch = existingBranch.get();
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

            String resolvedRoleName = isPartnerAdmin ? "MANAGER" : "ADMIN";
            Role role = roleRepository.findByName(resolvedRoleName)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò " + resolvedRoleName));

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
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện.");
            }

            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản."));

            boolean isPartnerAdmin = loggedInUser.getBranch() != null;
            if (isPartnerAdmin) {
                boolean targetIsManager = user.getRoles().stream().anyMatch(r -> "MANAGER".equalsIgnoreCase(r.getName()));
                if (!targetIsManager) {
                    return ResponseEntity.status(403).body("Không có quyền chỉnh sửa tài khoản quản trị khác.");
                }
                if (branchId == null || branchId.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Vui lòng chọn chi nhánh quản lý.");
                }
            }

            if (user.getTenant() == null || !user.getTenant().getTenantId().equals(loggedInUser.getTenant().getTenantId())) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác trên tài khoản thuộc tenant khác.");
            }

            if (!user.getEmail().equalsIgnoreCase(email)) {
                if (userRepository.findByEmail(email).isPresent()) {
                    return ResponseEntity.badRequest().body("Email đã tồn tại.");
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
                    return ResponseEntity.badRequest().body("Vui lòng nhập đầy đủ mã, tên và địa chỉ chi nhánh mới.");
                }
                Optional<Branch> existingBranch = branchRepository.findById(newBranchId.trim());
                if (existingBranch.isPresent()) {
                    branch = existingBranch.get();
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

            String resolvedRoleName = isPartnerAdmin ? "MANAGER" : "ADMIN";
            Role role = roleRepository.findByName(resolvedRoleName)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò."));
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
            if (loggedInUser == null || loggedInUser.getRoles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện.");
            }

            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản."));

            boolean isPartnerAdmin = loggedInUser.getBranch() != null;
            if (isPartnerAdmin) {
                boolean targetIsManager = user.getRoles().stream().anyMatch(r -> "MANAGER".equalsIgnoreCase(r.getName()));
                if (!targetIsManager) {
                    return ResponseEntity.status(403).body("Không có quyền xóa tài khoản quản trị khác.");
                }
            }

            if (user.getTenant() == null || !user.getTenant().getTenantId().equals(loggedInUser.getTenant().getTenantId())) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác trên tài khoản thuộc tenant khác.");
            }

            Optional<Employee> empOpt = employeeRepository.findByUserId(id);
            if (empOpt.isPresent()) {
                user.setActive(false);
                userRepository.save(user);
                return ResponseEntity.ok(Map.of("message", "Tài khoản có hồ sơ nhân sự liên kết. Đã vô hiệu hóa (khóa) tài khoản thay vì xóa vật lý.", "softDeleted", true));
            }

            userSessionRepository.deleteByUserId(id);
            userRepository.delete(user);
            return ResponseEntity.ok(Map.of("message", "Đã xóa tài khoản thành công.", "softDeleted", false));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
