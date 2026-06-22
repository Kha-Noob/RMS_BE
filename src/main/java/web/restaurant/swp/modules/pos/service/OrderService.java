package web.restaurant.swp.modules.pos.service;

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


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final TableRepository tableRepository;
    private final TableSessionRepository tableSessionRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryService inventoryService;
    private final LoyaltyService loyaltyService;

    // REQ-POS-01: List tables by branch
    public List<TableEntity> getTablesByBranch(String branchId) {
        return tableRepository.findByRoomBranchBranchId(branchId);
    }

    // REQ-POS-02: Open Table Session
    @Transactional
    public TableSession openTableSession(Long tableId, Long customerId) {
        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bàn."));
        if (!"EMPTY".equalsIgnoreCase(table.getStatus()) && !"RESERVED".equalsIgnoreCase(table.getStatus())) {
            throw new RuntimeException("Bàn đang được sử dụng.");
        }

        // Open session
        TableSession session = TableSession.builder()
                .table(table)
                .checkInTime(LocalDateTime.now())
                .status("ACTIVE")
                .paymentStatus("UNPAID")
                .build();

        table.setStatus("OCCUPIED");
        table.setGuestCount(2); // default
        tableRepository.save(table);

        return tableSessionRepository.save(session);
    }

    // REQ-POS-03 & 04: Add Item to Table Session Order
    @Transactional
    public OrderDetail addItemToSession(Long sessionId, Long variantId, int quantity, String notes) {
        TableSession session = tableSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiên hoạt động của bàn."));
        
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy biến thể sản phẩm."));

        // Find or create a PENDING order for this session
        List<Order> orders = orderRepository.findBySessionId(sessionId);
        Order activeOrder = orders.stream()
                .filter(o -> "PENDING".equalsIgnoreCase(o.getStatus()))
                .findFirst()
                .orElseGet(() -> {
                    Order o = Order.builder()
                            .session(session)
                            .orderDate(LocalDateTime.now())
                            .status("PENDING")
                            .totalAmount(0.0)
                            .branchId(session.getTable().getRoom().getBranch().getBranchId())
                            .build();
                    return orderRepository.save(o);
                });

        // Add detail
        OrderDetail detail = OrderDetail.builder()
                .order(activeOrder)
                .variant(variant)
                .quantity(quantity)
                .status("PENDING")
                .notes(notes)
                .price(variant.getPrice())
                .build();

        detail = orderDetailRepository.save(detail);

        // Update order total
        double orderTotal = orderDetailRepository.findByOrderId(activeOrder.getId()).stream()
                .mapToDouble(d -> d.getPrice() * d.getQuantity())
                .sum();
        activeOrder.setTotalAmount(orderTotal);
        orderRepository.save(activeOrder);

        return detail;
    }

    // REQ-POS-05: Send to Kitchen (Chốt giỏ hàng tạm thời, gửi KDS)
    @Transactional
    public void sendToKitchen(Long sessionId) {
        List<Order> orders = orderRepository.findBySessionId(sessionId);
        for (Order order : orders) {
            if ("PENDING".equalsIgnoreCase(order.getStatus())) {
                order.setStatus("SENT"); // Sent to kitchen
                orderRepository.save(order);

                List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
                for (OrderDetail detail : details) {
                    if ("PENDING".equalsIgnoreCase(detail.getStatus())) {
                        detail.setStatus("SENT");
                        orderDetailRepository.save(detail);
                    }
                }
            }
        }
        log.info("[KDS WEBSOCKET] Đẩy thông tin đơn hàng của phiên {} xuống Bếp", sessionId);
    }

    // REQ-POS-06: Split Bill (Item selection or equal division)
    @Transactional
    public List<Long> splitBill(Long sessionId, List<Long> detailIdsToExtract) {
        TableSession originalSession = tableSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiên."));

        // Create a new session for the split items
        TableSession newSession = TableSession.builder()
                .table(originalSession.getTable()) // same table
                .checkInTime(LocalDateTime.now())
                .status("ACTIVE")
                .paymentStatus("UNPAID")
                .build();
        newSession = tableSessionRepository.save(newSession);

        Order newOrder = Order.builder()
                .session(newSession)
                .orderDate(LocalDateTime.now())
                .status("SENT")
                .totalAmount(0.0)
                .branchId(originalSession.getTable().getRoom().getBranch().getBranchId())
                .build();
        newOrder = orderRepository.save(newOrder);

        double splitTotal = 0.0;
        for (Long detailId : detailIdsToExtract) {
            OrderDetail detail = orderDetailRepository.findById(detailId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn trong hóa đơn."));
            detail.setOrder(newOrder);
            orderDetailRepository.save(detail);
            splitTotal += detail.getPrice() * detail.getQuantity();
        }

        newOrder.setTotalAmount(splitTotal);
        orderRepository.save(newOrder);

        // Update original order totals
        List<Order> originalOrders = orderRepository.findBySessionId(sessionId);
        for (Order o : originalOrders) {
            double remainingTotal = orderDetailRepository.findByOrderId(o.getId()).stream()
                    .mapToDouble(d -> d.getPrice() * d.getQuantity())
                    .sum();
            o.setTotalAmount(remainingTotal);
            orderRepository.save(o);
        }

        List<Long> result = new ArrayList<>();
        result.add(originalSession.getId());
        result.add(newSession.getId());
        return result;
    }

    // REQ-POS-07: Merge Bill
    @Transactional
    public void mergeBill(Long sourceSessionId, Long targetSessionId) {
        TableSession sourceSession = tableSessionRepository.findById(sourceSessionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiên bàn nguồn."));
        TableSession targetSession = tableSessionRepository.findById(targetSessionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiên bàn đích."));

        // Move all source orders to target session
        List<Order> sourceOrders = orderRepository.findBySessionId(sourceSessionId);
        for (Order order : sourceOrders) {
            order.setSession(targetSession);
            orderRepository.save(order);
        }

        // Release source table
        TableEntity sourceTable = sourceSession.getTable();
        sourceTable.setStatus("EMPTY");
        sourceTable.setGuestCount(0);
        tableRepository.save(sourceTable);

        sourceSession.setStatus("COMPLETED");
        sourceSession.setCheckOutTime(LocalDateTime.now());
        tableSessionRepository.save(sourceSession);

        log.info("Ghép hóa đơn từ Session {} vào Session {}", sourceSessionId, targetSessionId);
    }

    // REQ-POS-08: Checkout & VNPay QR sandbox integration
    @Transactional
    public String generateVNPayQR(Long sessionId) {
        TableSession session = tableSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiên bàn cần thanh toán."));
        
        double billAmount = 0.0;
        List<Order> orders = orderRepository.findBySessionId(sessionId);
        for (Order o : orders) {
            billAmount += o.getTotalAmount();
        }

        // Apply automatic CRM tier discount if customer exists
        double discountAmount = 0.0;
        if (session.getCustomer() != null) {
            Customer customer = session.getCustomer();
            String tier = customer.getMembershipTier();
            double tierDiscountRate = 0.0;
            if ("Silver".equalsIgnoreCase(tier)) tierDiscountRate = 0.02;
            else if ("Gold".equalsIgnoreCase(tier)) tierDiscountRate = 0.05;
            else if ("Platinum".equalsIgnoreCase(tier)) tierDiscountRate = 0.10;

            discountAmount = billAmount * tierDiscountRate;
        }

        double finalAmount = Math.max(0.0, billAmount - discountAmount);

        // Sandbox simulated checkout link or QR code string
        // We'll return a payload to render a mock QR Scanner Modal in UI
        return "VNPAY-QR;Invoice#" + sessionId + ";Amount:" + finalAmount;
    }

    @Transactional
    public void confirmPayment(Long sessionId, double amountPaid) {
        confirmPayment(sessionId, amountPaid, "CASH");
    }

    @Transactional
    public void confirmPayment(Long sessionId, double amountPaid, String paymentMethod) {
        TableSession session = tableSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiên bàn cần thanh toán."));
        
        session.setPaymentStatus("PAID");
        session.setStatus("COMPLETED");
        session.setPaymentMethod(paymentMethod);
        session.setCheckOutTime(LocalDateTime.now());
        tableSessionRepository.save(session);

        TableEntity table = session.getTable();
        table.setStatus("EMPTY");
        table.setGuestCount(0);
        tableRepository.save(table);

        // Mark orders and details as served/completed
        List<Order> orders = orderRepository.findBySessionId(sessionId);
        for (Order o : orders) {
            o.setStatus("SERVED");
            orderRepository.save(o);
        }

        // Deduct inventory quantities based on recipes
        inventoryService.deductStockForSession(sessionId);

        // Loyalty calculations
        if (session.getCustomer() != null) {
            loyaltyService.accumulatePoints(session.getCustomer().getId(), amountPaid);
        }
        
        log.info("Thanh toán thành công ({}) cho Session {}, Bàn {}, Tổng tiền: {}", paymentMethod, sessionId, table.getName(), amountPaid);
    }
}
