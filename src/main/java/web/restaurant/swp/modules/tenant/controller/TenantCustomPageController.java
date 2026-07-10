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
}
