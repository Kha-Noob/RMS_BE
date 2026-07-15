package web.restaurant.swp.modules.tenant.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.auth.model.Role;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.RoleRepository;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.tenant.model.Tenant;
import web.restaurant.swp.modules.tenant.repository.TenantRepository;

import java.util.*;

@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<?> getAllTenants() {
        List<Tenant> tenants = tenantRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Tenant t : tenants) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tenantId", t.getTenantId());
            map.put("name", t.getName());
            map.put("domain", t.getDomain());
            map.put("isActive", t.isActive());
            map.put("isUsingSystemWeb", t.isUsingSystemWeb());

            // Find owner user (role cooperator) for this tenant
            Optional<User> ownerOpt = userRepository.findByTenantTenantId(t.getTenantId())
                    .stream()
                    .filter(u -> u.getRoles().stream().anyMatch(r -> "COOPERATOR".equalsIgnoreCase(r.getName())))
                    .findFirst();

            if (ownerOpt.isPresent()) {
                map.put("ownerEmail", ownerOpt.get().getEmail());
                map.put("ownerName", ownerOpt.get().getName());
            } else {
                map.put("ownerEmail", "");
                map.put("ownerName", "");
            }
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> createTenant(@RequestBody Map<String, Object> payload) {
        String name = (String) payload.get("name");
        String domain = (String) payload.get("domain");
        String ownerEmail = (String) payload.get("ownerEmail");
        String ownerName = (String) payload.get("ownerName");
        String ownerPassword = (String) payload.get("ownerPassword");
        Boolean isUsingSystemWeb = (Boolean) payload.get("isUsingSystemWeb");

        if (name == null || name.trim().isEmpty() ||
            ownerEmail == null || ownerEmail.trim().isEmpty() ||
            ownerName == null || ownerName.trim().isEmpty() ||
            ownerPassword == null || ownerPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Vui lòng điền đầy đủ các thông tin bắt buộc.");
        }

        if (userRepository.findByEmail(ownerEmail.trim()).isPresent()) {
            return ResponseEntity.badRequest().body("Email của chủ chuỗi đã tồn tại trong hệ thống.");
        }

        Tenant tenant = Tenant.builder()
                .name(name.trim())
                .domain(domain != null ? domain.trim() : "")
                .isActive(true)
                .isUsingSystemWeb(isUsingSystemWeb != null ? isUsingSystemWeb : false)
                .build();
        tenant = tenantRepository.save(tenant);

        Role cooperatorRole = roleRepository.findByName("COOPERATOR")
                .orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò COOPERATOR"));

        User owner = User.builder()
                .email(ownerEmail.trim())
                .name(ownerName.trim())
                .password(passwordEncoder.encode(ownerPassword))
                .tenant(tenant)
                .roles(new HashSet<>(Arrays.asList(cooperatorRole)))
                .isActive(true)
                .build();
        userRepository.save(owner);

        return ResponseEntity.ok(tenant);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTenant(@PathVariable String id, @RequestBody Map<String, Object> payload) {
        Tenant tenant = tenantRepository.findById(id).orElse(null);
        if (tenant == null) {
            return ResponseEntity.notFound().build();
        }

        if (payload.containsKey("name")) {
            tenant.setName((String) payload.get("name"));
        }
        if (payload.containsKey("domain")) {
            tenant.setDomain((String) payload.get("domain"));
        }
        if (payload.containsKey("isActive")) {
            tenant.setActive((Boolean) payload.get("isActive"));
        }
        if (payload.containsKey("isUsingSystemWeb")) {
            tenant.setUsingSystemWeb((Boolean) payload.get("isUsingSystemWeb"));
        }

        tenant = tenantRepository.save(tenant);
        return ResponseEntity.ok(tenant);
    }
}
