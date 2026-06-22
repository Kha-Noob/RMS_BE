package web.restaurant.swp.modules.loyalty.service;

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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyService {
    private final CustomerRepository customerRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;

    // REQ-LOY-01: Create customer profile
    @Transactional
    public Customer registerCustomer(String name, String phone, java.time.LocalDate birthDate) {
        Optional<Customer> existing = customerRepository.findByPhone(phone);
        if (existing.isPresent()) {
            throw new RuntimeException("Số điện thoại này đã được đăng ký tích điểm.");
        }
        Customer customer = Customer.builder()
                .name(name)
                .phone(phone)
                .birthDate(birthDate)
                .membershipTier("Bronze")
                .loyaltyPoints(0)
                .totalSpent(0.0)
                .build();
        return customerRepository.save(customer);
    }

    // REQ-LOY-02 & REQ-LOY-03: Accumulate points and tier checks
    @Transactional
    public void accumulatePoints(Long customerId, double paidAmount) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        int pointsEarned = (int) Math.floor(paidAmount * 0.01); // 1% points back

        // Save spent amount
        customer.setTotalSpent(customer.getTotalSpent() + paidAmount);
        customer.setLoyaltyPoints(customer.getLoyaltyPoints() + pointsEarned);

        // Update Member Rank
        double totalSpent = customer.getTotalSpent();
        if (totalSpent > 20000000.0) {
            customer.setMembershipTier("Platinum");
        } else if (totalSpent > 10000000.0) {
            customer.setMembershipTier("Gold");
        } else if (totalSpent > 5000000.0) {
            customer.setMembershipTier("Silver");
        } else {
            customer.setMembershipTier("Bronze");
        }
        customerRepository.save(customer);

        // Record point log
        LoyaltyTransaction transaction = LoyaltyTransaction.builder()
                .customer(customer)
                .points(pointsEarned)
                .type("Earn")
                .transactionDate(LocalDateTime.now())
                .build();
        loyaltyTransactionRepository.save(transaction);

        log.info("[CRM TÍCH ĐIỂM] Khách hàng {} (SĐT: {}) tích luỹ thêm {} điểm. Tổng điểm khả dụng: {}",
                customer.getName(), customer.getPhone(), pointsEarned, customer.getLoyaltyPoints());
    }

    // REQ-LOY-04: Redeem points for checkout
    @Transactional
    public double redeemPoints(Long customerId, int pointsToRedeem, double billTotal) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        // Validation 1: Points availability
        if (pointsToRedeem > customer.getLoyaltyPoints()) {
            throw new RuntimeException("Số điểm quy đổi vượt quá số điểm khả dụng của khách hàng.");
        }

        // Validation 2: Max 50% of the invoice value
        double valueInVnd = pointsToRedeem * 1.0; // 1 point = 1 VND
        if (valueInVnd > (billTotal * 0.5)) {
            throw new RuntimeException("Số tiền quy đổi từ điểm không được vượt quá 50% giá trị hóa đơn.");
        }

        // Deduct points
        customer.setLoyaltyPoints(customer.getLoyaltyPoints() - pointsToRedeem);
        customerRepository.save(customer);

        // Record transaction
        LoyaltyTransaction transaction = LoyaltyTransaction.builder()
                .customer(customer)
                .points(-pointsToRedeem)
                .type("Redeem")
                .transactionDate(LocalDateTime.now())
                .build();
        loyaltyTransactionRepository.save(transaction);

        log.info("[CRM TIÊU ĐIỂM] Khách hàng {} quy đổi {} điểm thành công.", customer.getName(), pointsToRedeem);
        return valueInVnd;
    }
}
