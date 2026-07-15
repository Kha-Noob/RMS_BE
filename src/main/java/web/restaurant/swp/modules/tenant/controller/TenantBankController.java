package web.restaurant.swp.modules.tenant.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.tenant.model.Tenant;
import web.restaurant.swp.modules.tenant.repository.TenantRepository;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cooperator/tenant/bank")
@RequiredArgsConstructor
@Slf4j
public class TenantBankController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @GetMapping
    public ResponseEntity<?> getBankConfig() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống."));
        }

        try {
            User user = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

            Tenant tenant = user.getTenant();
            if (tenant == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Người dùng không thuộc chuỗi/hợp tác nào."));
            }

            Map<String, String> data = new HashMap<>();
            data.put("bankName", tenant.getBankName() != null ? tenant.getBankName() : "");
            data.put("bankAccountNo", tenant.getBankAccountNo() != null ? tenant.getBankAccountNo() : "");
            data.put("bankAccountName", tenant.getBankAccountName() != null ? tenant.getBankAccountName() : "");
            data.put("bankBranch", tenant.getBankBranch() != null ? tenant.getBankBranch() : "");

            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Failed to fetch bank config", e);
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    @PutMapping
    public ResponseEntity<?> updateBankConfig(@RequestBody Map<String, String> payload) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống."));
        }

        try {
            User user = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

            Tenant tenant = user.getTenant();
            if (tenant == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Người dùng không thuộc chuỗi/hợp tác nào."));
            }

            if (payload.containsKey("bankName")) {
                tenant.setBankName(payload.get("bankName"));
            }
            if (payload.containsKey("bankAccountNo")) {
                tenant.setBankAccountNo(payload.get("bankAccountNo"));
            }
            if (payload.containsKey("bankAccountName")) {
                tenant.setBankAccountName(payload.get("bankAccountName"));
            }
            if (payload.containsKey("bankBranch")) {
                tenant.setBankBranch(payload.get("bankBranch"));
            }

            tenantRepository.save(tenant);
            log.info("Successfully updated bank config for tenant {}", tenant.getTenantId());

            return ResponseEntity.ok(Map.of("message", "Cập nhật cấu hình tài khoản ngân hàng thành công."));
        } catch (Exception e) {
            log.error("Failed to update bank config", e);
            return ResponseEntity.status(500).body(Map.of("message", "Cập nhật thất bại: " + e.getMessage()));
        }
    }
}
