package web.restaurant.swp.modules.kds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    public ResponseEntity<?> getKdsOrders(@RequestParam(required = false) String branchId) {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String targetBranchId = branchAccessService.validateAndGetBranchId(branchId, error);
            if (error.hasError()) return error.toResponse();

            List<Order> orders = orderRepository.findByBranchId(targetBranchId);
            List<Order> activeOrders = orders.stream()
                    .filter(o -> {
                        String s = o.getStatus();
                        return "SENT".equalsIgnoreCase(s)
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
                oMap.put("sessionOpenedAt", order.getSession() != null && order.getSession().getCheckInTime() != null ? order.getSession().getCheckInTime().toString() : null);
                oMap.put("status", order.getStatus());
                oMap.put("createdAt", order.getOrderDate() != null ? order.getOrderDate().toString() : "");

                List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
                List<Map<String, Object>> detailList = new ArrayList<>();
                for (OrderDetail d : details) {
                    if ("SENT".equalsIgnoreCase(d.getStatus()) || "COOKING".equalsIgnoreCase(d.getStatus()) || "READY".equalsIgnoreCase(d.getStatus())) {
                        Map<String, Object> dMap = new HashMap<>();
                        dMap.put("id", d.getId());
                        dMap.put("variantName", d.getVariant() != null ? d.getVariant().getName() : "");
                        dMap.put("productName", d.getVariant() != null && d.getVariant().getProduct() != null ? d.getVariant().getProduct().getName() : "");
                        dMap.put("quantity", d.getQuantity());
                        dMap.put("notes", d.getNotes() != null ? d.getNotes() : "");
                        dMap.put("status", d.getStatus());
                        detailList.add(dMap);
                    }
                }
                oMap.put("items", detailList);
                if (!detailList.isEmpty()) {
                    result.add(oMap);
                }
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/kds/status")
    public ResponseEntity<?> updateKdsStatus(
            @RequestParam(required = false) Long detailId,
            @RequestParam(required = false) Long orderId,
            @RequestParam String status) {
        try {
            if (detailId != null) {
                OrderDetail detail = orderDetailRepository.findById(detailId)
                        .orElseThrow(() -> new RuntimeException("Detail not found"));

                String entityBranchId = detail.getOrder().getBranchId();
                BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
                branchAccessService.validateEntityBranch(entityBranchId, error);
                if (error.hasError()) return error.toResponse();

                detail.setStatus(status);
                orderDetailRepository.save(detail);

                if ("SERVED".equalsIgnoreCase(status)) {
                    inventoryService.deductStockForOrderDetail(detail);
                }

                // Aggregate and roll up state to parent Order
                Order order = detail.getOrder();
                List<OrderDetail> allDetails = orderDetailRepository.findByOrderId(order.getId());
                boolean allReady = allDetails.stream().allMatch(d -> "READY".equalsIgnoreCase(d.getStatus()) || "SERVED".equalsIgnoreCase(d.getStatus()));
                boolean anyCooking = allDetails.stream().anyMatch(d -> "COOKING".equalsIgnoreCase(d.getStatus()));
                if (allReady) {
                    order.setStatus("READY");
                } else if (anyCooking) {
                    order.setStatus("COOKING");
                }
                orderRepository.save(order);

                if ("READY".equalsIgnoreCase(status)) {
                    KdsWebSocketHandler.broadcast("KDS_READY_ALERT:" + order.getSession().getTable().getName());
                } else {
                    KdsWebSocketHandler.broadcast("ORDER_STATE_CHANGED");
                }
            } else if (orderId != null) {
                Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new RuntimeException("Order not found"));

                String entityBranchId = order.getBranchId();
                BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
                branchAccessService.validateEntityBranch(entityBranchId, error);
                if (error.hasError()) return error.toResponse();

                order.setStatus(status);
                orderRepository.save(order);

                List<OrderDetail> details = orderDetailRepository.findByOrderId(orderId);
                for (OrderDetail d : details) {
                    d.setStatus(status);
                    orderDetailRepository.save(d);
                    if ("SERVED".equalsIgnoreCase(status)) {
                        inventoryService.deductStockForOrderDetail(d);
                    }
                }

                if ("READY".equalsIgnoreCase(status)) {
                    KdsWebSocketHandler.broadcast("KDS_READY_ALERT:" + order.getSession().getTable().getName());
                } else {
                    KdsWebSocketHandler.broadcast("ORDER_STATE_CHANGED");
                }
            } else {
                return ResponseEntity.badRequest().body("Either detailId or orderId must be provided");
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
