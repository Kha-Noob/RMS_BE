package web.restaurant.swp.modules.loyalty.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import web.restaurant.swp.modules.loyalty.model.*;
import web.restaurant.swp.modules.loyalty.repository.*;
import web.restaurant.swp.modules.loyalty.service.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class LoyaltyController {

    private final CustomerRepository customerRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final LoyaltyService loyaltyService;

    @PostMapping("/api/pos/customer/register")
    public ResponseEntity<?> registerCust(@RequestParam String name, @RequestParam String phone, @RequestParam String birthDate) {
        try {
            Customer cust = loyaltyService.registerCustomer(name, phone, LocalDate.parse(birthDate));
            return ResponseEntity.ok(cust);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
