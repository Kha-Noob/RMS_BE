package web.restaurant.swp.modules.kds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import web.restaurant.swp.config.KdsWebSocketHandler;
import web.restaurant.swp.modules.auth.model.*;
import web.restaurant.swp.modules.auth.repository.*;
import web.restaurant.swp.modules.pos.model.*;
import web.restaurant.swp.modules.pos.repository.*;
import web.restaurant.swp.modules.inventory.service.*;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class KdsController {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;

    private User getLoggedInUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private String getActiveBranchId() {
        return web.restaurant.swp.config.BranchContext.getActiveBranchId(getLoggedInUser());
    }

    @PostMapping("/api/kds/status")
    public ResponseEntity<?> updateKdsStatus(@RequestParam Long detailId, @RequestParam String status) {
        try {
            OrderDetail detail = orderDetailRepository.findById(detailId)
                    .orElseThrow(() -> new RuntimeException("Detail not found"));
            detail.setStatus(status);
            orderDetailRepository.save(detail);

            if ("SERVED".equalsIgnoreCase(status)) {
                inventoryService.deductStockForOrderDetail(detail);
            }

            if ("READY".equalsIgnoreCase(status)) {
                KdsWebSocketHandler.broadcast("KDS_READY_ALERT:" + detail.getOrder().getSession().getTable().getName());
            } else {
                KdsWebSocketHandler.broadcast("ORDER_STATE_CHANGED");
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
