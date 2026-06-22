package web.restaurant.swp.modules.procurement.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.RestController;
import org.springframework.web.bind.annotation.GetMapping;

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

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProcurementController {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final ProcurementService procurementService;
    private final InventoryItemRepository inventoryItemRepository;

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

    @GetMapping("/api/procurement/po/details/{poId}")
    public ResponseEntity<?> getPoDetails(@PathVariable Long poId) {
        try {
            PurchaseOrder po = purchaseOrderRepository.findById(poId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
            List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderId(poId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", po.getId());
            response.put("poCode", po.getPoCode());
            response.put("supplierName", po.getSupplier().getName());
            response.put("status", po.getStatus());
            response.put("totalAmount", po.getTotalAmount());
            response.put("deliveryDeadline", po.getDeliveryDeadline() != null ? po.getDeliveryDeadline().toString() : "");
            
            List<Map<String, Object>> itemDetails = new ArrayList<>();
            for (PurchaseOrderItem item : items) {
                Map<String, Object> iMap = new HashMap<>();
                iMap.put("itemId", item.getItem().getId());
                iMap.put("itemName", item.getItem().getName());
                iMap.put("sku", item.getItem().getSku());
                iMap.put("unit", item.getItem().getUnit());
                iMap.put("quantity", item.getQuantity());
                iMap.put("unitPrice", item.getUnitPrice());
                itemDetails.add(iMap);
            }
            response.put("items", itemDetails);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/procurement/po/approve")
    public ResponseEntity<?> approvePo(@RequestBody Map<String, Long> payload) {
        try {
            Long poId = payload.get("poId");
            procurementService.approvePurchaseOrder(poId);
            return ResponseEntity.ok("Successfully approved Purchase Order");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/procurement/po/grn")
    public ResponseEntity<?> createGoodsReceipt(@RequestBody GrnRequest request) {
        try {
            List<Long> itemIds = request.getItemIds();
            List<Double> acceptedQty = request.getAcceptedQty();
            List<Double> rejectedQty = request.getRejectedQty();
            
            if (itemIds == null || itemIds.isEmpty()) {
                List<PurchaseOrderItem> poItems = purchaseOrderItemRepository.findByPurchaseOrderId(request.getPoId());
                itemIds = new ArrayList<>();
                acceptedQty = new ArrayList<>();
                rejectedQty = new ArrayList<>();
                for (PurchaseOrderItem poItem : poItems) {
                    itemIds.add(poItem.getItem().getId());
                    acceptedQty.add(poItem.getQuantity());
                    rejectedQty.add(0.0);
                }
            }
            
            GoodsReceipt grn = procurementService.createGoodsReceipt(
                request.getPoId(),
                request.getReceivedBy() != null ? request.getReceivedBy() : "Admin/Manager",
                itemIds,
                acceptedQty,
                rejectedQty
            );
            return ResponseEntity.ok(grn);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/procurement/po/match")
    public ResponseEntity<?> performThreeWayMatch(@RequestBody MatchRequest request) {
        try {
            boolean match = procurementService.performThreeWayMatch(request.getPoId(), request.getActualInvoiceAmount());
            return ResponseEntity.ok(match);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/procurement/po/create")
    public ResponseEntity<?> createPurchaseOrder(@RequestBody PoCreateRequest request) {
        try {
            String branchId = getActiveBranchId();
            PurchaseOrder po = procurementService.createPurchaseOrder(
                branchId,
                request.getSupplierId(),
                request.getDeliveryDeadline(),
                request.getItemIds(),
                request.getQuantities(),
                request.getUnitPrices()
            );
            return ResponseEntity.ok(po);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @lombok.Data
    public static class PoCreateRequest {
        private Long supplierId;
        private LocalDate deliveryDeadline;
        private List<Long> itemIds;
        private List<Double> quantities;
        private List<Double> unitPrices;
    }

    @lombok.Data
    public static class GrnRequest {
        private Long poId;
        private String receivedBy;
        private List<Long> itemIds;
        private List<Double> acceptedQty;
        private List<Double> rejectedQty;
    }

    @lombok.Data
    public static class MatchRequest {
        private Long poId;
        private Double actualInvoiceAmount;
    }
}
