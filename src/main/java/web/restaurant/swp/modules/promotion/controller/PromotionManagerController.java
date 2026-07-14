package web.restaurant.swp.modules.promotion.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.promotion.model.Promotion;
import web.restaurant.swp.modules.promotion.repository.PromotionRepository;
import web.restaurant.swp.modules.tenant.model.Tenant;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/manager/promotions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class PromotionManagerController {

    private final PromotionRepository promotionRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    // Get all promotions of current manager's tenant
    @GetMapping
    public ResponseEntity<?> getTenantPromotions() {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống."));
        }
        
        Tenant tenant = user.getTenant();
        if (tenant == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tài khoản của bạn không thuộc bất cứ chuỗi nhà hàng (Tenant) nào."));
        }

        List<Promotion> promotions = promotionRepository.findByTenantTenantId(tenant.getTenantId());
        LocalDate today = LocalDate.now();
        List<Promotion> activePromotions = new ArrayList<>();
        
        for (Promotion p : promotions) {
            if (p.getEndDate() != null && p.getEndDate().isBefore(today)) {
                promotionRepository.delete(p);
            } else {
                activePromotions.add(p);
            }
        }

        return ResponseEntity.ok(activePromotions);
    }

    // Create a new promotion
    @PostMapping
    public ResponseEntity<?> createPromotion(@RequestBody Map<String, Object> payload) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống."));
        }

        Tenant tenant = user.getTenant();
        if (tenant == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tài khoản của bạn không thuộc bất cứ chuỗi nhà hàng (Tenant) nào."));
        }

        String name = (String) payload.get("name");
        String promoCode = (String) payload.get("promoCode");
        boolean autoGenerate = Boolean.TRUE.equals(payload.get("autoGenerate"));
        String type = (String) payload.get("type"); // PercentDiscount, FlatDiscount
        double discountValue = ((Number) payload.getOrDefault("discountValue", 0.0)).doubleValue();
        double minOrderValue = ((Number) payload.getOrDefault("minOrderValue", 0.0)).doubleValue();
        
        String startDateStr = (String) payload.get("startDate");
        String endDateStr = (String) payload.get("endDate");
        
        LocalDate startDate = startDateStr != null ? LocalDate.parse(startDateStr) : LocalDate.now();
        LocalDate endDate = endDateStr != null ? LocalDate.parse(endDateStr) : LocalDate.now().plusDays(30);

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tên chương trình khuyến mãi không được để trống."));
        }

        if (type == null || (!type.equalsIgnoreCase("PercentDiscount") && !type.equalsIgnoreCase("FlatDiscount"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Loại giảm giá không hợp lệ (PercentDiscount hoặc FlatDiscount)."));
        }

        if (discountValue <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Giá trị giảm giá phải lớn hơn 0."));
        }

        String finalCode;
        if (autoGenerate) {
            finalCode = generateUniqueCode();
        } else {
            if (promoCode == null || promoCode.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Mã khuyến mãi không được trống khi không chọn tự động sinh."));
            }
            finalCode = promoCode.trim().toUpperCase();
            
            Optional<Promotion> existing = promotionRepository.findByPromoCodeAndIsActiveTrue(finalCode);
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Mã khuyến mãi '" + finalCode + "' đã tồn tại và đang hoạt động. Vui lòng chọn mã khác."));
            }
        }

        Promotion promotion = Promotion.builder()
                .name(name)
                .promoCode(finalCode)
                .type(type)
                .discountValue(discountValue)
                .minOrderValue(minOrderValue)
                .startDate(startDate)
                .endDate(endDate)
                .isActive(true)
                .tenant(tenant)
                .build();

        Promotion saved = promotionRepository.save(promotion);
        return ResponseEntity.ok(saved);
    }

    // Delete promotion
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePromotion(@PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống."));
        }

        Tenant tenant = user.getTenant();
        if (tenant == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tài khoản của bạn không thuộc bất cứ chuỗi nhà hàng (Tenant) nào."));
        }

        Optional<Promotion> promoOpt = promotionRepository.findById(id);
        if (promoOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy chương trình khuyến mãi."));
        }

        Promotion promo = promoOpt.get();
        if (promo.getTenant() == null || !promo.getTenant().getTenantId().equals(tenant.getTenantId())) {
            return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền xóa chương trình khuyến mãi của Tenant khác."));
        }

        promotionRepository.delete(promo);
        return ResponseEntity.ok(Map.of("message", "Đã xóa chương trình khuyến mãi thành công."));
    }

    private String generateUniqueCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rnd = new Random();
        String code;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
            code = sb.toString();
        } while (promotionRepository.findByPromoCodeAndIsActiveTrue(code).isPresent());
        
        return code;
    }
}
