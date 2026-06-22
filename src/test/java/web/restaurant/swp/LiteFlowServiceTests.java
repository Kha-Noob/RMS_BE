package web.restaurant.swp;

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


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class LiteFlowServiceTests {

    @Autowired
    private AuthService authService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private ProcurementService procurementService;
    @Autowired
    private HRService hrService;
    @Autowired
    private LoyaltyService loyaltyService;
    @Autowired
    private PromotionEngine promotionEngine;
    @Autowired
    private AIService aiService;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TableRepository tableRepository;
    @Autowired
    private TableSessionRepository tableSessionRepository;
    @Autowired
    private ProductVariantRepository productVariantRepository;
    @Autowired
    private BranchInventoryRepository branchInventoryRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderDetailRepository orderDetailRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private PromotionRepository promotionRepository;
    @Autowired
    private ForgotClockRequestRepository forgotClockRequestRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private BranchTransferRepository branchTransferRepository;
    @Autowired
    private EmployeeAttendanceRepository employeeAttendanceRepository;
    @Autowired
    private PayrollRunRepository payrollRunRepository;
    @Autowired
    private PayrollEntryRepository payrollEntryRepository;
    @Autowired
    private InventoryItemRepository inventoryItemRepository;
    @Autowired
    private BranchRepository branchRepository;

    // --- AUTHENTICATION & SECURITY TESTS (REQ-AUTH) ---
    @Test
    void testAuthenticationLockout() {
        // Find seeder user Nguyễn Văn A
        String email = "manager@liteflow.com";
        
        // Attempt login with wrong password 5 times to trigger lockout
        for (int i = 1; i <= 4; i++) {
            assertThrows(RuntimeException.class, () -> {
                authService.authenticate(email, "WrongPwd!", "127.0.0.1");
            });
        }
        
        // The 5th attempt must lock the account
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            authService.authenticate(email, "WrongPwd!", "127.0.0.1");
        });
        assertTrue(ex.getMessage().contains("khóa") || ex.getMessage().contains("lock"));
    }

    @Test
    void testPasswordStrengthAndReset() {
        // Request token
        String token = authService.requestPasswordReset("manager@liteflow.com");
        assertNotNull(token);

        // Weak password must throw validation error
        assertThrows(RuntimeException.class, () -> {
            authService.resetPassword(token, "weak");
        });

        // Strong password must succeed
        authService.resetPassword(token, "NewStrong123!");
        
        // Authenticate with new password
        User user = authService.authenticate("manager@liteflow.com", "NewStrong123!", "127.0.0.1");
        assertNotNull(user);
    }

    // --- POS BILLING TESTS (REQ-POS) ---
    @Test
    void testOpenTableSessionAndAddProduct() {
        // Locate a pre-seeded empty table (e.g., Table 1)
        TableEntity table = tableRepository.findAll().stream()
                .filter(t -> "EMPTY".equalsIgnoreCase(t.getStatus()))
                .findFirst()
                .orElseThrow();

        // 1. Open Session
        TableSession session = orderService.openTableSession(table.getId(), null);
        assertNotNull(session);
        assertEquals("ACTIVE", session.getStatus());
        assertEquals("OCCUPIED", table.getStatus());

        // 2. Fetch a variant
        ProductVariant variant = productVariantRepository.findAll().get(0);

        // 3. Add to cart
        OrderDetail detail = orderService.addItemToSession(session.getId(), variant.getId(), 2, "ít hành");
        assertNotNull(detail);
        assertEquals(2, detail.getQuantity());
        assertEquals("ít hành", detail.getNotes());
    }

    @Test
    void testMergeBill() {
        // Select pre-seeded Table 2 and Table 5 (both occupied in DataSeeder)
        TableSession sessionSource = tableSessionRepository.findByTableIdAndStatus(2L, "ACTIVE").orElseThrow();
        TableSession sessionTarget = tableSessionRepository.findByTableIdAndStatus(5L, "ACTIVE").orElseThrow();

        // Merge bill
        orderService.mergeBill(sessionSource.getId(), sessionTarget.getId());

        // Source session should be inactive, source table should be empty
        assertEquals("COMPLETED", sessionSource.getStatus());
        assertEquals("EMPTY", sessionSource.getTable().getStatus());
    }

    // --- INVENTORY RECIPE TESTS (REQ-INV) ---
    @Test
    void testRecipeStockDeduction() {
        // Fetch current beef stock (MAT-002) for branch-1
        BranchInventory initialStock = branchInventoryRepository.findByBranchBranchIdAndItemSku("branch-1", "MAT-002").orElseThrow();
        double startQty = initialStock.getQuantity();

        // Table 2 has an active session ordering Phở Bò which needs beef (MAT-002)
        TableSession session = tableSessionRepository.findByTableIdAndStatus(2L, "ACTIVE").orElseThrow();

        // Pay & Checkout
        orderService.confirmPayment(session.getId(), 195000.0);

        // Beef stock must be subtracted by (Phở Bò quantity * quantityNeeded)
        // Table 2 has 2 Phở Bò * 0.15 kg beef = 0.3 kg deducted
        BranchInventory updatedStock = branchInventoryRepository.findByBranchBranchIdAndItemSku("branch-1", "MAT-002").orElseThrow();
        assertEquals(startQty - 0.3, updatedStock.getQuantity(), 0.001);
    }

    // --- PROCUREMENT TESTS (REQ-PRO) ---
    @Test
    void testThreeWayMatchingCheck() {
        // Locate supplier A
        Supplier supplier = supplierRepository.findByCode("SUP-001").orElseThrow();

        // Create PO
        PurchaseOrder po = procurementService.createPurchaseOrder("branch-1", supplier.getId(), LocalDate.now().plusDays(2),
                Arrays.asList(1L), Arrays.asList(10.0), Arrays.asList(100000.0));
        assertNotNull(po);

        // Create GRN (Received all 10 standard items)
        procurementService.createGoodsReceipt(po.getId(), "Warehouse Staff F", Arrays.asList(1L), Arrays.asList(10.0), Arrays.asList(0.0));

        // 3-way matching: Calculated value = 10 * 100K = 1,000,000 VND
        // Invoice with 2.1% deviation (1,025,000 VND) must be rejected
        boolean rejected = procurementService.performThreeWayMatch(po.getId(), 1025000.0);
        assertFalse(rejected);

        // Invoice with 1% deviation (1,010,000 VND) must pass
        boolean accepted = procurementService.performThreeWayMatch(po.getId(), 1010000.0);
        assertTrue(accepted);
    }

    // --- HR & SCHEDULING TESTS (REQ-HR) ---
    @Test
    void testScheduleLimits() {
        // Select cashier Nguyễn Văn A
        Employee emp = employeeRepository.findById(1L).orElseThrow();
        LocalDate date = LocalDate.now().plusWeeks(1);

        // Assign 1st shift - Success
        assertNotNull(hrService.assignShift(emp.getId(), 1L, date));
        
        // Assign 2nd shift - Success
        assertNotNull(hrService.assignShift(emp.getId(), 2L, date));

        // Assign 3rd shift - Fails (limit is max 2 shifts per employee per day)
        assertThrows(RuntimeException.class, () -> {
            hrService.assignShift(emp.getId(), 3L, date);
        });
    }

    // --- LOYALTY CRM TESTS (REQ-LOY) ---
    @Test
    void testLoyaltyCRMUpgradeAndRedeem() {
        // Register customer
        Customer customer = loyaltyService.registerCustomer("Customer Test", "0900000001", LocalDate.of(2000, 1, 1));
        assertNotNull(customer);

        // Accumulate 6,000,000 VND -> Silver upgrade (threshold: 5M VND)
        loyaltyService.accumulatePoints(customer.getId(), 6000000.0);
        assertEquals("Silver", customer.getMembershipTier());
        assertEquals(60000, customer.getLoyaltyPoints()); // 1% = 60,000 points

        // Redeem 10,000 points for a 50,000 VND bill (Valid - less than 50% limit)
        double discount = loyaltyService.redeemPoints(customer.getId(), 10000, 50000.0);
        assertEquals(10000.0, discount);
        assertEquals(50000, customer.getLoyaltyPoints()); // remaining
    }

    // --- PROMOTION ENGINE TESTS (REQ-PROMO) ---
    @Test
    void testB1G1PromotionAndCoupon() {
        // Create table session
        TableSession session = orderService.openTableSession(1L, null);
        
        // Add variant triggering Phở Bò (Product 1 / variant 1)
        orderService.addItemToSession(session.getId(), 1L, 1, "");
        
        List<Order> orders = orderRepository.findBySessionId(session.getId());
        Order activeOrder = orders.stream().filter(o -> "PENDING".equalsIgnoreCase(o.getStatus())).findFirst().orElseThrow();
        List<OrderDetail> details = orderDetailRepository.findByOrderId(activeOrder.getId());
        
        // Apply B1G1 rules - should insert free Gỏi Cuốn (Product 3 / variant 5)
        promotionEngine.processBuyOneGetOne(activeOrder, details);
        
        List<OrderDetail> updatedDetails = orderDetailRepository.findByOrderId(activeOrder.getId());
        boolean hasFreeGoiCuon = updatedDetails.stream()
                .anyMatch(d -> d.getVariant().getProduct().getId() == 3 && d.getPrice() == 0.0);
        assertTrue(hasFreeGoiCuon);

        // Validate coupon constraints
        // Coupon code "LITEFLOW10" requires minimum 200,000 VND
        assertThrows(RuntimeException.class, () -> {
            promotionEngine.validateCoupon("LITEFLOW10", 150000.0);
        });

        Promotion coupon = promotionEngine.validateCoupon("LITEFLOW10", 250000.0);
        assertNotNull(coupon);
    }

    // --- 2FA & OTP SECURITY TESTS (REQ-AUTH) ---
    @Test
    void test2FAFlow() {
        User user = userRepository.findByEmail("manager@liteflow.com").orElseThrow();
        String secret = "JBSWY3DPEHPK3PXP"; // Base32 style secret
        
        // Enable 2FA
        authService.enable2FA(user.getId(), secret);
        
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(updated.isTwoFactorEnabled());
        assertEquals(secret, updated.getTwoFactorSecret());

        // Verify with code
        assertTrue(authService.verifyTotp(secret, "123456"));
        
        // Disable 2FA
        authService.disable2FA(user.getId());
        User disabled = userRepository.findById(user.getId()).orElseThrow();
        assertFalse(disabled.isTwoFactorEnabled());
        assertNull(disabled.getTwoFactorSecret());
    }

    @Test
    void testEmailOtpVerification() {
        String email = "manager@liteflow.com";
        // verifyEmailOtp should return false for invalid code
        assertFalse(authService.verifyEmailOtp(email, "000000"));
        
        // Trigger sending OTP
        authService.sendEmailOtp(email);
        
        // Since we can't extract the random OTP from the service, verifying wrong OTP should still be false
        assertFalse(authService.verifyEmailOtp(email, "999999"));
    }

    // --- INVENTORY MOVEMENT, STOCKTAKE & TRANSFER TESTS (REQ-INV & REQ-MB) ---
    @Test
    void testInventoryStocktakeAndTransfer() {
        // Find material MAT-001 (Bột mì) in branch-1
        BranchInventory bInv = branchInventoryRepository.findByBranchBranchIdAndItemSku("branch-1", "MAT-001").orElseThrow();
        double startQty = bInv.getQuantity();

        // 1. Stocktake: Adjust actual quantity to 42.5 kg
        inventoryService.executeStocktake("branch-1", bInv.getItem().getId(), 42.5);
        BranchInventory updatedInv = branchInventoryRepository.findByBranchBranchIdAndItemSku("branch-1", "MAT-001").orElseThrow();
        assertEquals(42.5, updatedInv.getQuantity());

        // 2. Transfer request from branch-1 to branch-2
        BranchInventory targetInvBefore = branchInventoryRepository.findByBranchBranchIdAndItemSku("branch-2", "MAT-001")
                .orElseGet(() -> branchInventoryRepository.save(BranchInventory.builder()
                        .branch(branchRepository.findById("branch-2").orElseThrow())
                        .item(bInv.getItem())
                        .quantity(0.0)
                        .reorderPoint(5.0)
                        .build()));
        double targetStartQty = targetInvBefore.getQuantity();

        BranchTransfer transfer = inventoryService.createTransferRequest("branch-1", "branch-2",
                Arrays.asList(bInv.getItem().getId()), Arrays.asList(5.0));
        assertNotNull(transfer);
        assertEquals("PENDING", transfer.getStatus());

        // Approve and execute transfer
        inventoryService.approveAndExecuteTransfer(transfer.getId());

        // Verify stock counts
        BranchInventory sourceUpdated = branchInventoryRepository.findByBranchBranchIdAndItemSku("branch-1", "MAT-001").orElseThrow();
        BranchInventory targetUpdated = branchInventoryRepository.findByBranchBranchIdAndItemSku("branch-2", "MAT-001").orElseThrow();

        assertEquals(42.5 - 5.0, sourceUpdated.getQuantity(), 0.001);
        assertEquals(targetStartQty + 5.0, targetUpdated.getQuantity(), 0.001);
        assertEquals("RECEIVED", transfer.getStatus());
    }

    // --- HR ATTENDANCE & PAYROLL TESTS (REQ-HR) ---
    @Test
    void testHRClockInOutForgotClockAndLeave() {
        // Fetch Nguyễn Văn F (Hourly employee)
        Employee emp = employeeRepository.findAll().stream()
                .filter(e -> "Nguyễn Văn F".equalsIgnoreCase(e.getUser().getName()))
                .findFirst()
                .orElseThrow();

        // 1. Clock In
        EmployeeAttendance att = hrService.clockIn(emp.getId());
        assertNotNull(att);
        assertNotNull(att.getClockIn());
        assertEquals(LocalDate.now(), att.getDate());

        // 2. Clock Out
        EmployeeAttendance attOut = hrService.clockOut(emp.getId());
        assertNotNull(attOut.getClockOut());
        assertTrue(attOut.getHoursWorked() >= 0.0);

        // 3. Leave request
        LeaveRequest leave = LeaveRequest.builder()
                .employee(emp)
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(12))
                .leaveType("Nghỉ có lương")
                .reason("Nghỉ phép năm")
                .status("PENDING")
                .build();
        leave = leaveRequestRepository.save(leave);
        hrService.approveLeaveRequest(leave.getId());
        assertEquals("APPROVED", leave.getStatus());

        // Assert attendance records are generated for these 3 days
        Optional<EmployeeAttendance> day1 = employeeAttendanceRepository.findByEmployeeIdAndDate(emp.getId(), LocalDate.now().plusDays(10));
        Optional<EmployeeAttendance> day2 = employeeAttendanceRepository.findByEmployeeIdAndDate(emp.getId(), LocalDate.now().plusDays(11));
        Optional<EmployeeAttendance> day3 = employeeAttendanceRepository.findByEmployeeIdAndDate(emp.getId(), LocalDate.now().plusDays(12));
        assertTrue(day1.isPresent());
        assertEquals(0.0, day1.get().getHoursWorked());
        assertTrue(day2.isPresent());
        assertEquals(0.0, day2.get().getHoursWorked());
        assertTrue(day3.isPresent());
        assertEquals(0.0, day3.get().getHoursWorked());

        // 4. Forgot Clock Request
        ForgotClockRequest forgot = ForgotClockRequest.builder()
                .employee(emp)
                .date(LocalDate.now().minusDays(2))
                .clockType("CLOCK_IN")
                .timeProposed("08:00")
                .status("PENDING")
                .build();
        forgot = forgotClockRequestRepository.save(forgot);
        hrService.approveForgotClock(forgot.getId());
        assertEquals("APPROVED", forgot.getStatus());

        EmployeeAttendance clockInAtt = employeeAttendanceRepository.findByEmployeeIdAndDate(emp.getId(), LocalDate.now().minusDays(2)).orElseThrow();
        assertNotNull(clockInAtt.getClockIn());
    }

    @Test
    void testPayrollCalculation() {
        // Run monthly payroll for dev branch seeder employees
        PayrollRun run = hrService.runMonthlyPayroll("2026-05", "HR Officer");
        assertNotNull(run);
        assertEquals("2026-05", run.getPeriod());

        List<PayrollEntry> entries = payrollEntryRepository.findByPayrollRunId(run.getId());
        assertFalse(entries.isEmpty());

        // Verify manager entry (Fixed type)
        PayrollEntry managerEntry = entries.stream()
                .filter(e -> "Nguyễn Văn A".equalsIgnoreCase(e.getEmployee().getUser().getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(15000000.0, managerEntry.getBasePay());
        assertTrue(managerEntry.getNetPay() >= 0.0);
    }

    // --- AI ANALYSIS REPORT TESTS (REQ-AI) ---
    @Test
    void testAIServiceReportFallback() {
        String report = aiService.analyzeDailyReport("branch-1", "Tóm tắt tình hình bán hàng hôm nay.");
        assertNotNull(report);
        assertTrue(report.contains("LiteFlow AI") || report.contains("Tổng doanh thu"));
        assertTrue(report.contains("Doanh thu") || report.contains("đơn phục vụ"));
    }

    @Test
    void testServedStatusRealTimeDeduction() {
        ProductVariant variant = productVariantRepository.findBySku("PHOBOS").orElseThrow();
        // Check initial stock of Meat/Beef (item ID 2L) in branch-1
        BranchInventory binv = branchInventoryRepository.findByBranchBranchIdAndItemId("branch-1", 2L).orElseThrow();
        double initialQty = binv.getQuantity();

        // Open session and add item
        TableSession session = orderService.openTableSession(1L, null);
        OrderDetail detail = orderService.addItemToSession(session.getId(), variant.getId(), 2, "Test served deduction");
        
        // Assert not yet marked as deducted
        assertFalse(detail.isDeducted());

        // Call the served-status deduction logic directly
        inventoryService.deductStockForOrderDetail(detail);

        // Verify it was marked as deducted
        OrderDetail updatedDetail = orderDetailRepository.findById(detail.getId()).orElseThrow();
        assertTrue(updatedDetail.isDeducted());

        // Verify the inventory was deducted by exactly: 2 * 0.15 = 0.3 kg
        BranchInventory binvAfter = branchInventoryRepository.findByBranchBranchIdAndItemId("branch-1", 2L).orElseThrow();
        assertEquals(initialQty - 0.3, binvAfter.getQuantity(), 0.0001);
    }
}

