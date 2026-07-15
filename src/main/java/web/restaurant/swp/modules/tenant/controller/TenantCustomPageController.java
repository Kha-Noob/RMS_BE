package web.restaurant.swp.modules.tenant.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.tenant.model.TenantCustomPage;
import web.restaurant.swp.modules.tenant.service.TenantCustomPageService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class TenantCustomPageController {

    private final TenantCustomPageService tenantCustomPageService;
    private final UserRepository userRepository;
    private final web.restaurant.swp.modules.tenant.repository.TenantRepository tenantRepository;
    private final web.restaurant.swp.modules.booking.repository.BookingRepository bookingRepository;
    private final web.restaurant.swp.modules.promotion.repository.PromotionRepository promotionRepository;
    private final web.restaurant.swp.modules.review.repository.CustomerReviewRepository customerReviewRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    // AI generate page theme config
    @PostMapping("/admin/custom-pages/generate")
    public ResponseEntity<?> generatePageTheme(@RequestBody Map<String, Object> payload) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống."));
        }
        String prompt = (String) payload.get("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ý tưởng/Từ khóa không được trống."));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> currentSettings = (Map<String, Object>) payload.get("currentSettings");
        try {
            Map<String, Object> result = tenantCustomPageService.generateThemeFromPrompt(prompt, currentSettings);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi sinh giao diện: " + e.getMessage()));
        }
    }

    // Save custom page configuration
    @PostMapping("/admin/custom-pages/save")
    public ResponseEntity<?> saveCustomPage(@RequestBody TenantCustomPage pageSettings) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống."));
        }
        
        String tenantId = pageSettings.getTenantId();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            // Default to logged-in user's tenant if not specified
            if (user.getTenant() != null) {
                tenantId = user.getTenant().getTenantId();
            } else {
                return ResponseEntity.badRequest().body(Map.of("message", "Thiếu mã Tenant."));
            }
        }

        try {
            TenantCustomPage saved = tenantCustomPageService.saveCustomPage(tenantId, pageSettings, user);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi khi lưu cấu hình: " + e.getMessage()));
        }
    }

    // Get current custom page of the logged-in user's tenant
    @GetMapping("/admin/custom-pages/my-page")
    public ResponseEntity<?> getMyCustomPage() {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống."));
        }
        if (user.getTenant() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bạn không thuộc bất cứ Tenant nào."));
        }
        try {
            TenantCustomPage page = tenantCustomPageService.getCustomPageByTenantId(user.getTenant().getTenantId())
                    .orElse(new TenantCustomPage());
            return ResponseEntity.ok(page);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    // Public endpoint: Get all published custom pages for explore section
    @GetMapping("/public/custom-pages")
    public ResponseEntity<?> getPublishedCustomPages() {
        try {
            List<TenantCustomPage> pages = tenantCustomPageService.getAllPublishedCustomPages();
            return ResponseEntity.ok(pages);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    // Public endpoint: Get specific custom page configuration for dynamic rendering
    @GetMapping("/public/custom-pages/{tenantId}")
    public ResponseEntity<?> getCustomPageByTenantId(@PathVariable String tenantId) {
        try {
            TenantCustomPage page = tenantCustomPageService.getCustomPageByTenantId(tenantId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy trang của nhà hàng này."));
            return ResponseEntity.ok(page);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Public endpoint: Get system statistics and About Us info
    @GetMapping("/public/about-us")
    public ResponseEntity<?> getAboutUsInfo() {
        try {
            long partnerCount = tenantRepository.count();
            long bookingCount = bookingRepository.count();
            
            // Add a base of 10000 to diner count for maturity demonstration if it's small/empty
            long happyDiners = bookingCount > 0 ? bookingCount : 10000;
            
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("aboutTag", "Về chúng tôi - RMS");
            data.put("aboutTitle", "Sứ mệnh kết nối tinh hoa ẩm thực");
            data.put("aboutDesc", "RMS được sinh ra với mong muốn rút ngắn khoảng cách giữa thực khách và các nhà hàng cao cấp. Chúng tôi không chỉ cung cấp dịch vụ tìm kiếm và đặt bàn nhanh chóng, mà còn xây dựng cộng đồng đánh giá ẩm thực uy tín, nơi các tín đồ ẩm thực chia sẻ trải nghiệm chân thực.");
            data.put("partnerRestaurants", partnerCount + "+");
            data.put("happyDiners", happyDiners + "+");
            data.put("dedicatedService", "24/7");
            data.put("aboutOverlay", "Đồng hành cùng thực khách Việt");
            
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    // Public endpoint: Get Footer contact & platform info
    @GetMapping("/public/footer")
    public ResponseEntity<?> getFooterInfo() {
        try {
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("footerDesc", "Kết nối những tâm hồn ẩm thực với các nhà hàng sang trọng và uy tín. Tìm kiếm, đánh giá và đặt bàn trực tuyến dễ dàng.");
            data.put("hotline", "1900 1234 (24/7)");
            data.put("email", "support@rms.com");
            data.put("location", "Hanoi, Vietnam");
            data.put("facebook", "https://facebook.com/rms");
            data.put("instagram", "https://instagram.com/rms");
            
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    @GetMapping("/public/promotions")
    public ResponseEntity<?> getActivePromotions() {
        try {
            List<web.restaurant.swp.modules.promotion.model.Promotion> promotions = promotionRepository.findByIsActiveTrue();
            java.time.LocalDate today = java.time.LocalDate.now();
            List<web.restaurant.swp.modules.promotion.model.Promotion> active = new java.util.ArrayList<>();
            
            for (web.restaurant.swp.modules.promotion.model.Promotion p : promotions) {
                if (p.getEndDate() != null && p.getEndDate().isBefore(today)) {
                    promotionRepository.delete(p);
                } else {
                    active.add(p);
                }
            }
            return ResponseEntity.ok(active);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    // Public endpoint: Get cooperation packages
    @GetMapping("/public/cooperation/packages")
    public ResponseEntity<?> getCooperationPackages() {
        try {
            List<Map<String, Object>> packages = new java.util.ArrayList<>();
            
            Map<String, Object> pkg1 = new java.util.LinkedHashMap<>();
            pkg1.put("code", "EVENT_ONLY");
            pkg1.put("name", "Gói Sự Kiện & Bán Vé (Event Only)");
            pkg1.put("titleVi", "Chỉ hợp tác đăng Event & Ưu đãi");
            pkg1.put("titleEn", "Cooperate on Events only");
            pkg1.put("price", 5000000.0);
            pkg1.put("descVi", "Phí khởi tạo ban đầu: 5.000.000đ. Nhận dòng tiền bán vé sự kiện trên nền tảng, chiết khấu hoa hồng 10%. Không sử dụng phần mềm quản lý nội bộ chuỗi.");
            pkg1.put("descEn", "One-time setup fee: 5,000,000đ. Sell tickets, 10% commission. No system dashboard access.");
            pkg1.put("description", "Hỗ trợ các công cụ quản lý sự kiện và thanh toán tiền vé chuyên nghiệp.");
            packages.add(pkg1);

            Map<String, Object> pkg2 = new java.util.LinkedHashMap<>();
            pkg2.put("code", "APP_SUBSCRIPTION");
            pkg2.put("name", "Gói Thuê Phần Mềm Hàng Tháng (Subscription)");
            pkg2.put("titleVi", "Thuê phần mềm quản trị chuỗi (Thuê theo tháng)");
            pkg2.put("titleEn", "App Subscription (Monthly)");
            pkg2.put("price", 2000000.0);
            pkg2.put("descVi", "Phí thuê: 2.000.000đ / tháng. Sử dụng trọn bộ hệ thống quản trị RMS, POS chi nhánh, KDS, Kho, sơ đồ bàn, chấm công. Chiết khấu hoa hồng sự kiện 5%.");
            pkg2.put("descEn", "Fee: 2,000,000đ / month. Access entire RMS management, POS, KDS, Inventory. 5% event ticket commission.");
            pkg2.put("description", "Sử dụng đầy đủ tính năng RMS và cập nhật liên tục hàng tháng.");
            packages.add(pkg2);

            Map<String, Object> pkg3 = new java.util.LinkedHashMap<>();
            pkg3.put("code", "APP_LIFETIME");
            pkg3.put("name", "Gói Trọn Đời (Lifetime License)");
            pkg3.put("titleVi", "Mua đứt phần mềm quản trị chuỗi (Vĩnh viễn)");
            pkg3.put("titleEn", "App Purchase (Lifetime)");
            pkg3.put("price", 50000000.0);
            pkg3.put("descVi", "Phí mua đứt trọn gói: 50.000.000đ. Sở hữu vĩnh viễn hệ thống phần mềm quản lý chuỗi nhà hàng và POS mà không phát sinh thêm chi phí duy trì. Chiết khấu hoa hồng sự kiện 5%.");
            pkg3.put("descEn", "One-time fee: 50,000,000đ. Lifetime access to RMS management and branch POS. 5% event ticket commission.");
            pkg3.put("description", "Mua bản quyền vĩnh viễn, không giới hạn thời gian sử dụng.");
            packages.add(pkg3);

            return ResponseEntity.ok(packages);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
     }

    // Public endpoint: Get latest approved customer reviews
    @GetMapping("/public/reviews")
    public ResponseEntity<?> getLatestReviews() {
        try {
            List<web.restaurant.swp.modules.review.model.CustomerReview> reviews = customerReviewRepository.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsApproved()))
                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
                .limit(10)
                .toList();
            return ResponseEntity.ok(reviews);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }
}
