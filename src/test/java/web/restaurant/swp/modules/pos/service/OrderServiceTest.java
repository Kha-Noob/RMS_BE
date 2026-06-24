package web.restaurant.swp.modules.pos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import web.restaurant.swp.modules.pos.model.*;
import web.restaurant.swp.modules.pos.repository.*;
import web.restaurant.swp.modules.inventory.model.*;
import web.restaurant.swp.modules.inventory.repository.*;
import web.restaurant.swp.modules.inventory.service.InventoryService;
import web.restaurant.swp.modules.loyalty.model.Customer;
import web.restaurant.swp.modules.loyalty.service.LoyaltyService;
import web.restaurant.swp.modules.branch.model.Branch;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private TableRepository tableRepository;
    @Mock
    private TableSessionRepository tableSessionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderDetailRepository orderDetailRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private LoyaltyService loyaltyService;

    @InjectMocks
    private OrderService orderService;

    private Branch testBranch;
    private Room testRoom;
    private TableEntity testTable;
    private TableSession testSession;
    private Product testProduct;
    private ProductVariant testVariant;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        testBranch = Branch.builder()
                .branchId("branch-1")
                .name("Test Branch")
                .address("123 Test St")
                .phone("0123456789")
                .isActive(true)
                .build();

        testRoom = Room.builder()
                .id(1L)
                .name("Floor 1")
                .branch(testBranch)
                .build();

        testTable = TableEntity.builder()
                .id(1L)
                .name("Table 1")
                .room(testRoom)
                .status("EMPTY")
                .capacity(4)
                .guestCount(0)
                .build();

        testSession = TableSession.builder()
                .id(1L)
                .table(testTable)
                .checkInTime(LocalDateTime.now())
                .status("ACTIVE")
                .paymentStatus("UNPAID")
                .build();

        testProduct = Product.builder()
                .id(1L)
                .name("Pho Bo")
                .description("Beef noodle soup")
                .isActive(true)
                .build();

        testVariant = ProductVariant.builder()
                .id(1L)
                .product(testProduct)
                .name("Regular")
                .price(95000.0)
                .originalPrice(45000.0)
                .sku("PHOBOS")
                .isTopping(false)
                .build();

        testOrder = Order.builder()
                .id(1L)
                .session(testSession)
                .orderDate(LocalDateTime.now())
                .status("PENDING")
                .totalAmount(0.0)
                .branchId("branch-1")
                .build();
    }

    // === Happy Path Tests ===

    @Test
    void openTableSession_ShouldCreateSession_WhenTableIsEmpty() {
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(tableSessionRepository.save(any(TableSession.class))).thenAnswer(invocation -> {
            TableSession s = invocation.getArgument(0);
            s.setId(10L);
            return s;
        });

        TableSession result = orderService.openTableSession(1L, null);

        assertNotNull(result);
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("UNPAID", result.getPaymentStatus());
        assertEquals("OCCUPIED", testTable.getStatus());
        verify(tableRepository).save(testTable);
        verify(tableSessionRepository).save(any(TableSession.class));
    }

    @Test
    void addItemToSession_ShouldCreateOrderAndDetail_WhenValidInput() {
        when(tableSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(productVariantRepository.findById(1L)).thenReturn(Optional.of(testVariant));
        when(orderRepository.findBySessionId(1L)).thenReturn(new ArrayList<>());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(orderDetailRepository.save(any(OrderDetail.class))).thenAnswer(inv -> {
            OrderDetail d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });
        when(orderDetailRepository.findByOrderId(1L)).thenReturn(new ArrayList<>());

        OrderDetail result = orderService.addItemToSession(1L, 1L, 2, "ít hành");

        assertNotNull(result);
        assertEquals(2, result.getQuantity());
        assertEquals("ít hành", result.getNotes());
        assertEquals(95000.0, result.getPrice());
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(orderDetailRepository).save(any(OrderDetail.class));
    }

    @Test
    void sendToKitchen_ShouldUpdateStatus_WhenOrderIsPending() {
        Order pendingOrder = Order.builder()
                .id(1L)
                .session(testSession)
                .status("PENDING")
                .totalAmount(190000.0)
                .branchId("branch-1")
                .build();
        OrderDetail detail = OrderDetail.builder()
                .id(1L)
                .order(pendingOrder)
                .variant(testVariant)
                .quantity(2)
                .status("PENDING")
                .price(95000.0)
                .build();

        when(orderRepository.findBySessionId(1L)).thenReturn(List.of(pendingOrder));
        when(orderDetailRepository.findByOrderId(1L)).thenReturn(List.of(detail));

        orderService.sendToKitchen(1L);

        assertEquals("SENT", pendingOrder.getStatus());
        assertEquals("SENT", detail.getStatus());
        verify(orderRepository).save(pendingOrder);
        verify(orderDetailRepository).save(detail);
    }

    @Test
    void mergeBill_ShouldMoveOrdersAndReleaseSourceTable() {
        TableEntity sourceTable = TableEntity.builder()
                .id(2L)
                .name("Table 2")
                .room(testRoom)
                .status("OCCUPIED")
                .build();
        TableSession sourceSession = TableSession.builder()
                .id(2L)
                .table(sourceTable)
                .status("ACTIVE")
                .paymentStatus("UNPAID")
                .build();
        TableSession targetSession = TableSession.builder()
                .id(3L)
                .table(testTable)
                .status("ACTIVE")
                .paymentStatus("UNPAID")
                .build();

        Order sourceOrder = Order.builder()
                .id(2L)
                .session(sourceSession)
                .status("SENT")
                .totalAmount(95000.0)
                .branchId("branch-1")
                .build();

        when(tableSessionRepository.findById(2L)).thenReturn(Optional.of(sourceSession));
        when(tableSessionRepository.findById(3L)).thenReturn(Optional.of(targetSession));
        when(orderRepository.findBySessionId(2L)).thenReturn(List.of(sourceOrder));

        orderService.mergeBill(2L, 3L);

        assertEquals("COMPLETED", sourceSession.getStatus());
        assertNotNull(sourceSession.getCheckOutTime());
        assertEquals("EMPTY", sourceTable.getStatus());
        assertEquals(0, sourceTable.getGuestCount());
        verify(tableRepository).save(sourceTable);
        verify(tableSessionRepository).save(sourceSession);
    }

    @Test
    void generateVNPayQR_ShouldReturnQRData_WhenSessionExists() {
        Order order = Order.builder()
                .id(1L)
                .session(testSession)
                .status("SENT")
                .totalAmount(190000.0)
                .branchId("branch-1")
                .build();

        when(tableSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(orderRepository.findBySessionId(1L)).thenReturn(List.of(order));

        String result = orderService.generateVNPayQR(1L);

        assertNotNull(result);
        assertTrue(result.contains("VNPAY-QR"));
        assertTrue(result.contains("Invoice#1"));
        assertTrue(result.contains("190000.0"));
    }

    @Test
    void generateVNPayQR_ShouldApplyTierDiscount_WhenCustomerIsPlatinum() {
        Customer platinumCustomer = Customer.builder()
                .id(1L)
                .name("VIP Customer")
                .phone("0900000001")
                .membershipTier("Platinum")
                .loyaltyPoints(100000)
                .totalSpent(25000000.0)
                .build();
        testSession.setCustomer(platinumCustomer);

        Order order = Order.builder()
                .id(1L)
                .session(testSession)
                .status("SENT")
                .totalAmount(200000.0)
                .branchId("branch-1")
                .build();

        when(tableSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(orderRepository.findBySessionId(1L)).thenReturn(List.of(order));

        String result = orderService.generateVNPayQR(1L);

        // Platinum gets 10% discount: 200000 * 0.90 = 180000
        assertTrue(result.contains("180000.0"));
    }

    @Test
    void confirmPayment_ShouldCompleteSession_WhenValidInput() {
        when(tableSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(orderRepository.findBySessionId(1L)).thenReturn(List.of(testOrder));

        orderService.confirmPayment(1L, 190000.0, "CASH");

        assertEquals("PAID", testSession.getPaymentStatus());
        assertEquals("COMPLETED", testSession.getStatus());
        assertEquals("CASH", testSession.getPaymentMethod());
        assertNotNull(testSession.getCheckOutTime());
        assertEquals("EMPTY", testTable.getStatus());
        assertEquals(0, testTable.getGuestCount());
        assertEquals("SERVED", testOrder.getStatus());
        verify(inventoryService).deductStockForSession(1L);
        verify(tableRepository).save(testTable);
    }

    // === Not Found Tests ===

    @Test
    void openTableSession_ShouldThrowException_WhenTableNotFound() {
        when(tableRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                orderService.openTableSession(999L, null));
        assertTrue(ex.getMessage().contains("Không tìm thấy bàn"));
    }

    @Test
    void addItemToSession_ShouldThrowException_WhenSessionNotFound() {
        when(tableSessionRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                orderService.addItemToSession(999L, 1L, 1, ""));
        assertTrue(ex.getMessage().contains("Không tìm thấy phiên hoạt động"));
    }

    @Test
    void addItemToSession_ShouldThrowException_WhenVariantNotFound() {
        when(tableSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(productVariantRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                orderService.addItemToSession(1L, 999L, 1, ""));
        assertTrue(ex.getMessage().contains("Không tìm thấy biến thể sản phẩm"));
    }

    // === Validation / Business Rule Fail Tests ===

    @Test
    void openTableSession_ShouldThrowException_WhenTableIsOccupied() {
        testTable.setStatus("OCCUPIED");
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                orderService.openTableSession(1L, null));
        assertTrue(ex.getMessage().contains("Bàn đang được sử dụng"));
    }

    @Test
    void splitBill_ShouldThrowException_WhenSessionNotFound() {
        when(tableSessionRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                orderService.splitBill(999L, List.of(1L)));
        assertTrue(ex.getMessage().contains("Không tìm thấy phiên"));
    }

    // === Edge Case Tests ===

    @Test
    void addItemToSession_ShouldCalculateTotalCorrectly_WhenMultipleItems() {
        ProductVariant variant2 = ProductVariant.builder()
                .id(2L)
                .product(testProduct)
                .name("Large")
                .price(120000.0)
                .originalPrice(55000.0)
                .sku("PHOBOS-L")
                .isTopping(false)
                .build();

        OrderDetail existingDetail = OrderDetail.builder()
                .id(1L)
                .order(testOrder)
                .variant(testVariant)
                .quantity(1)
                .price(95000.0)
                .build();

        when(tableSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(productVariantRepository.findById(2L)).thenReturn(Optional.of(variant2));
        when(orderRepository.findBySessionId(1L)).thenReturn(List.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderDetailRepository.save(any(OrderDetail.class))).thenAnswer(inv -> {
            OrderDetail d = inv.getArgument(0);
            d.setId(2L);
            return d;
        });
        when(orderDetailRepository.findByOrderId(1L)).thenReturn(
                List.of(existingDetail, OrderDetail.builder()
                        .id(2L)
                        .order(testOrder)
                        .variant(variant2)
                        .quantity(2)
                        .price(120000.0)
                        .build()));

        OrderDetail result = orderService.addItemToSession(1L, 2L, 2, "");

        assertNotNull(result);
        // Total = 95000*1 + 120000*2 = 335000
        verify(orderRepository).save(argThat(order -> order.getTotalAmount() == 335000.0));
    }

    @Test
    void openTableSession_ShouldAllowReservedTable_WhenTableIsReserved() {
        testTable.setStatus("RESERVED");
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(tableSessionRepository.save(any(TableSession.class))).thenAnswer(inv -> {
            TableSession s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        TableSession result = orderService.openTableSession(1L, null);

        assertNotNull(result);
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("OCCUPIED", testTable.getStatus());
    }

    @Test
    void confirmPayment_ShouldCallLoyalty_WhenCustomerExists() {
        Customer customer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .phone("0900000001")
                .membershipTier("Bronze")
                .loyaltyPoints(0)
                .totalSpent(0.0)
                .build();
        testSession.setCustomer(customer);

        when(tableSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(orderRepository.findBySessionId(1L)).thenReturn(List.of(testOrder));

        orderService.confirmPayment(1L, 190000.0, "BANK_TRANSFER");

        verify(loyaltyService).accumulatePoints(1L, 190000.0);
        assertEquals("BANK_TRANSFER", testSession.getPaymentMethod());
    }

    @Test
    void confirmPayment_ShouldNotCallLoyalty_WhenNoCustomer() {
        testSession.setCustomer(null);
        when(tableSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(orderRepository.findBySessionId(1L)).thenReturn(List.of(testOrder));

        orderService.confirmPayment(1L, 190000.0, "CASH");

        verify(loyaltyService, never()).accumulatePoints(anyLong(), anyDouble());
    }

    @Test
    void sendToKitchen_ShouldSkipNonPendingOrders() {
        Order sentOrder = Order.builder()
                .id(1L)
                .session(testSession)
                .status("SENT")
                .totalAmount(95000.0)
                .branchId("branch-1")
                .build();

        when(orderRepository.findBySessionId(1L)).thenReturn(List.of(sentOrder));

        orderService.sendToKitchen(1L);

        verify(orderRepository, never()).save(any());
        verify(orderDetailRepository, never()).findByOrderId(anyLong());
    }

    @Test
    void getTablesByBranch_ShouldReturnTables_WhenBranchExists() {
        TableEntity table2 = TableEntity.builder()
                .id(2L)
                .name("Table 2")
                .room(testRoom)
                .status("EMPTY")
                .capacity(6)
                .build();
        when(tableRepository.findByRoomBranchBranchId("branch-1")).thenReturn(List.of(testTable, table2));

        List<TableEntity> result = orderService.getTablesByBranch("branch-1");

        assertEquals(2, result.size());
        assertEquals("Table 1", result.get(0).getName());
        assertEquals("Table 2", result.get(1).getName());
    }

    @Test
    void splitBill_ShouldCreateNewSessionAndTransferItems() {
        OrderDetail detail = OrderDetail.builder()
                .id(1L)
                .order(testOrder)
                .variant(testVariant)
                .quantity(2)
                .price(95000.0)
                .build();

        when(tableSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(tableSessionRepository.save(any(TableSession.class))).thenAnswer(inv -> {
            TableSession s = inv.getArgument(0);
            s.setId(5L);
            return s;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(2L);
            return o;
        });
        when(orderDetailRepository.findById(1L)).thenReturn(Optional.of(detail));
        when(orderRepository.findBySessionId(1L)).thenReturn(List.of(testOrder));
        when(orderDetailRepository.findByOrderId(1L)).thenReturn(new ArrayList<>());

        List<Long> result = orderService.splitBill(1L, List.of(1L));

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0)); // original session
        assertEquals(5L, result.get(1)); // new session
        verify(tableSessionRepository, times(1)).save(any(TableSession.class));
    }
}
