package web.restaurant.swp.modules.kds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

import web.restaurant.swp.config.KdsWebSocketHandler;
import web.restaurant.swp.modules.auth.model.*;
import web.restaurant.swp.modules.auth.repository.*;
import web.restaurant.swp.modules.pos.model.*;
import web.restaurant.swp.modules.pos.repository.*;
import web.restaurant.swp.modules.inventory.service.*;
import web.restaurant.swp.modules.branch.service.BranchAccessService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class KdsController {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final BranchAccessService branchAccessService;

    @GetMapping("/api/kds/orders")
    public ResponseEntity<?> getKdsOrders() {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            List<Order> orders = orderRepository.findByBranchId(branchId);
            List<Order> activeOrders = orders.stream()
                    .filter(o -> {
                        String s = o.getStatus();
                        return "PENDING".equalsIgnoreCase(s) || "SENT".equalsIgnoreCase(s)
                                || "COOKING".equalsIgnoreCase(s) || "READY".equalsIgnoreCase(s);
                    })
                    .sorted((a, b) -> b.getOrderDate().compareTo(a.getOrderDate()))
                    .collect(Collectors.toList());

            List<Map<String, Object>> result = new ArrayList<>();
            for (Order order : activeOrders) {
                Map<String, Object> oMap = new HashMap<>();
                oMap.put("id", order.getId());
                oMap.put("tableId", order.getSession() != null ? order.getSession().getTable().getId() : null);
                oMap.put("tableName", order.getSession() != null ? order.getSession().getTable().getName() : "");
                oMap.put("status", order.getStatus());
                oMap.put("createdAt", order.getOrderDate() != null ? order.getOrderDate().toString() : "");

                List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
                List<Map<String, Object>> detailList = new ArrayList<>();
                for (OrderDetail d : details) {
                    Map<String, Object> dMap = new HashMap<>();
                    dMap.put("id", d.getId());
                    dMap.put("variantName", d.getVariant() != null ? d.getVariant().getName() : "");
                    dMap.put("productName", d.getVariant() != null && d.getVariant().getProduct() != null ? d.getVariant().getProduct().getName() : "");
                    dMap.put("quantity", d.getQuantity());
                    dMap.put("notes", d.getNotes() != null ? d.getNotes() : "");
                    dMap.put("status", d.getStatus());
                    detailList.add(dMap);
                }
                oMap.put("items", detailList);
                result.add(oMap);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public static class KdsStatusRequest {
        public Long orderId;
        public Long detailId;
        public String status;
    }

    @PostMapping("/api/kds/status")
    public ResponseEntity<?> updateKdsStatus(@RequestBody KdsStatusRequest req) {
        try {
            if (req.orderId != null) {
                Order order = orderRepository.findById(req.orderId)
                        .orElseThrow(() -> new RuntimeException("Order not found"));

                String entityBranchId = order.getBranchId();
                BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
                branchAccessService.validateEntityBranch(entityBranchId, error);
                if (error.hasError()) return error.toResponse();

                order.setStatus(req.status);
                orderRepository.save(order);

                List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
                for (OrderDetail detail : details) {
                    detail.setStatus(req.status);
                    orderDetailRepository.save(detail);

                    if ("SERVED".equalsIgnoreCase(req.status)) {
                        inventoryService.deductStockForOrderDetail(detail);
                    }
                }

                if ("READY".equalsIgnoreCase(req.status)) {
                    KdsWebSocketHandler.broadcast("KDS_READY_ALERT:" + (order.getSession() != null ? order.getSession().getTable().getName() : "#" + order.getId()));
                } else {
                    KdsWebSocketHandler.broadcast("ORDER_STATE_CHANGED");
                }
                return ResponseEntity.ok().build();
            } else if (req.detailId != null) {
                OrderDetail detail = orderDetailRepository.findById(req.detailId)
                        .orElseThrow(() -> new RuntimeException("Detail not found"));

                String entityBranchId = detail.getOrder().getBranchId();
                BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
                branchAccessService.validateEntityBranch(entityBranchId, error);
                if (error.hasError()) return error.toResponse();

                detail.setStatus(req.status);
                orderDetailRepository.save(detail);

                if ("SERVED".equalsIgnoreCase(req.status)) {
                    inventoryService.deductStockForOrderDetail(detail);
                }

                if ("READY".equalsIgnoreCase(req.status)) {
                    KdsWebSocketHandler.broadcast("KDS_READY_ALERT:" + detail.getOrder().getSession().getTable().getName());
                } else {
                    KdsWebSocketHandler.broadcast("ORDER_STATE_CHANGED");
                }
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.badRequest().body("Either orderId or detailId must be provided");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
