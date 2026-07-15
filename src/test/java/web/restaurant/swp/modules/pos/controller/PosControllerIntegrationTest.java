package web.restaurant.swp.modules.pos.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import web.restaurant.swp.modules.auth.model.*;
import web.restaurant.swp.modules.auth.repository.*;
import web.restaurant.swp.modules.auth.service.AuthService;
import web.restaurant.swp.modules.pos.model.*;
import web.restaurant.swp.modules.pos.repository.*;
import web.restaurant.swp.modules.pos.service.OrderService;
import web.restaurant.swp.modules.inventory.model.ProductVariant;
import web.restaurant.swp.modules.inventory.model.Product;
import web.restaurant.swp.modules.inventory.repository.ProductRepository;
import web.restaurant.swp.modules.inventory.repository.CategoryRepository;
import web.restaurant.swp.modules.inventory.repository.ProductVariantRepository;
import web.restaurant.swp.modules.loyalty.repository.CustomerRepository;
import web.restaurant.swp.modules.branch.model.Branch;
import web.restaurant.swp.modules.branch.model.BankSetting;
import web.restaurant.swp.modules.branch.repository.BranchRepository;
import web.restaurant.swp.modules.branch.repository.BankSettingRepository;
import web.restaurant.swp.modules.hr.repository.EmployeeRepository;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class PosControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private TableRepository tableRepository;
    @MockitoBean
    private TableSessionRepository tableSessionRepository;
    @MockitoBean
    private OrderRepository orderRepository;
    @MockitoBean
    private OrderDetailRepository orderDetailRepository;
    @MockitoBean
    private ProductRepository productRepository;
    @MockitoBean
    private CategoryRepository categoryRepository;
    @MockitoBean
    private ProductVariantRepository productVariantRepository;
    @MockitoBean
    private CustomerRepository customerRepository;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private RoomRepository roomRepository;
    @MockitoBean
    private BranchRepository branchRepository;
    @MockitoBean
    private RoleRepository roleRepository;
    @MockitoBean
    private UserSessionRepository userSessionRepository;
    @MockitoBean
    private EmployeeRepository employeeRepository;
    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private BankSettingRepository bankSettingRepository;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private AuditLogRepository auditLogRepository;

    private MockMvc mockMvc;

    private Branch testBranch;
    private Room testRoom;
    private TableEntity testTable;
    private TableSession testSession;
    private User testUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .build();

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

        Role cashierRole = Role.builder().id(1L).name("CASHIER").build();
        testUser = User.builder()
                .id(1L)
                .email("cashier@liteflow.com")
                .password("$2a$10$encoded")
                .name("Test Cashier")
                .isActive(true)
                .roles(new HashSet<>(Set.of(cashierRole)))
                .branch(testBranch)
                .build();
    }

    // === Happy Path Tests ===

    @Test
    @WithMockUser(username = "cashier@liteflow.com", roles = {"CASHIER"})
    void openSession_ShouldReturnOk_WhenValidTableId() throws Exception {
        TableSession newSession = TableSession.builder()
                .id(10L)
                .table(testTable)
                .status("ACTIVE")
                .paymentStatus("UNPAID")
                .build();

        when(userRepository.findByEmail("cashier@liteflow.com")).thenReturn(Optional.of(testUser));
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(orderService.openTableSession(eq(1L), isNull())).thenReturn(newSession);

        mockMvc.perform(post("/api/pos/session/open")
                        .param("tableId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(orderService).openTableSession(eq(1L), isNull());
    }

    @Test
    @WithMockUser(username = "cashier@liteflow.com", roles = {"CASHIER"})
    void openSession_ShouldReturnBadRequest_WhenTableNotFound() throws Exception {
        when(userRepository.findByEmail("cashier@liteflow.com")).thenReturn(Optional.of(testUser));
        when(tableRepository.findById(999L)).thenReturn(Optional.empty());
        when(orderService.openTableSession(eq(999L), isNull()))
                .thenThrow(new RuntimeException("Không tìm thấy bàn."));

        mockMvc.perform(post("/api/pos/session/open")
                        .param("tableId", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Không tìm thấy bàn."));
    }

    @Test
    @WithMockUser(username = "cashier@liteflow.com", roles = {"CASHIER"})
    void addToCart_ShouldReturnOrderDetail_WhenValidInput() throws Exception {
        Product product = Product.builder().id(1L).name("Pho Bo").build();
        ProductVariant variant = ProductVariant.builder()
                .id(1L).product(product).name("Regular").price(95000.0).sku("PHOBOS").build();
        Order order = Order.builder().id(1L).session(testSession).branchId("branch-1").build();
        OrderDetail detail = OrderDetail.builder()
                .id(1L).order(order).variant(variant).quantity(2).price(95000.0).status("PENDING").notes("ít hành").build();

        when(userRepository.findByEmail("cashier@liteflow.com")).thenReturn(Optional.of(testUser));
        when(tableSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(orderService.addItemToSession(eq(1L), eq(1L), eq(2), eq("ít hành"))).thenReturn(detail);

        mockMvc.perform(post("/api/pos/order/add")
                        .param("sessionId", "1")
                        .param("variantId", "1")
                        .param("quantity", "2")
                        .param("notes", "ít hành"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.price").value(95000.0))
                .andExpect(jsonPath("$.notes").value("ít hành"));
    }

    // === Not Found / Error Tests ===

    @Test
    @WithMockUser(username = "cashier@liteflow.com", roles = {"CASHIER"})
    void sendToKitchen_ShouldReturnBadRequest_WhenSessionNotFound() throws Exception {
        when(userRepository.findByEmail("cashier@liteflow.com")).thenReturn(Optional.of(testUser));
        doThrow(new RuntimeException("Không tìm thấy phiên.")).when(orderService).sendToKitchen(eq(999L));

        mockMvc.perform(post("/api/pos/order/send")
                        .param("sessionId", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Không tìm thấy phiên."));
    }

    @Test
    @WithMockUser(username = "cashier@liteflow.com", roles = {"CASHIER"})
    void getActiveSession_ShouldReturn404_WhenNoActiveSession() throws Exception {
        when(tableSessionRepository.findByTableIdAndStatus(99L, "ACTIVE")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/pos/session/active")
                        .param("tableId", "99"))
                .andExpect(status().isNotFound());
    }

    // === Security / Permission Tests ===

    @Test
    @WithMockUser(username = "cashier@liteflow.com", roles = {"CASHIER"})
    void getRooms_ShouldReturnEmptyList_WhenNoRoomsForBranch() throws Exception {
        when(userRepository.findByEmail("cashier@liteflow.com")).thenReturn(Optional.of(testUser));
        when(roomRepository.findByBranchBranchId("branch-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/pos/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(username = "cashier@liteflow.com", roles = {"CASHIER"})
    void getRooms_ShouldReturnOk_WhenAuthenticated() throws Exception {
        when(userRepository.findByEmail("cashier@liteflow.com")).thenReturn(Optional.of(testUser));
        when(roomRepository.findByBranchBranchId("branch-1")).thenReturn(List.of(testRoom));

        mockMvc.perform(get("/api/pos/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Floor 1"));
    }

    @Test
    @WithMockUser(username = "cashier@liteflow.com", roles = {"CASHIER"})
    void addRoom_ShouldReturn403_WhenUserIsCashier() throws Exception {
        when(userRepository.findByEmail("cashier@liteflow.com")).thenReturn(Optional.of(testUser));

        mockMvc.perform(post("/api/pos/rooms/add")
                        .param("name", "New Room"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "cashier@liteflow.com", roles = {"CASHIER"})
    void requestVNPayQR_ShouldReturnQrData_WhenValidSession() throws Exception {
        when(userRepository.findByEmail("cashier@liteflow.com")).thenReturn(Optional.of(testUser));
        when(orderService.generateVNPayQR(1L)).thenReturn("VNPAY-QR;Invoice#1;Amount:190000.0");

        mockMvc.perform(post("/api/pos/checkout/vnpay")
                        .param("sessionId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrData").value("VNPAY-QR;Invoice#1;Amount:190000.0"));
    }

    @Test
    @WithMockUser(username = "cashier@liteflow.com", roles = {"CASHIER"})
    void finalizePayment_ShouldReturnOk_WhenValidPayment() throws Exception {
        when(userRepository.findByEmail("cashier@liteflow.com")).thenReturn(Optional.of(testUser));
        when(tableSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

        mockMvc.perform(post("/api/pos/checkout/confirm")
                        .param("sessionId", "1")
                        .param("amount", "190000")
                        .param("paymentMethod", "CASH"))
                .andExpect(status().isOk());

        verify(orderService).confirmPayment(1L, 190000.0, "CASH");
    }
}
