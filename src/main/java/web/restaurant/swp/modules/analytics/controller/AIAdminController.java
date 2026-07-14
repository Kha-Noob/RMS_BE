package web.restaurant.swp.modules.analytics.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.inventory.model.Product;
import web.restaurant.swp.modules.inventory.model.Category;
import web.restaurant.swp.modules.inventory.repository.ProductRepository;
import web.restaurant.swp.modules.inventory.repository.CategoryRepository;
import web.restaurant.swp.modules.tenant.model.Tenant;
import web.restaurant.swp.modules.tenant.repository.TenantRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AIAdminController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @PostMapping(value = "/chatbot-import", consumes = "multipart/form-data")
    public ResponseEntity<?> importChatbotData(@RequestParam("file") MultipartFile file) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống."));
        }

        Tenant tenant = user.getTenant();
        if (tenant == null) {
            // Fallback for global admin: use the first tenant in database
            tenant = tenantRepository.findAll().stream().findFirst().orElse(null);
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File tải lên không được để trống."));
        }

        try {
            String content = "";
            String filename = file.getOriginalFilename();
            if (filename != null && filename.toLowerCase().endsWith(".docx")) {
                content = readDocx(file);
            } else {
                content = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));
            }

            Map<String, Object> result = processImportForTenant(content, tenant);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error importing chatbot configuration from file", e);
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi nạp dữ liệu: " + e.getMessage()));
        }
    }

    private String readDocx(MultipartFile file) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                sb.append(para.getText()).append("\n");
            }
            return sb.toString();
        }
    }

    private Map<String, Object> processImportForTenant(String content, Tenant tenant) {
        String[] lines = content.split("\r?\n");
        int updatedCount = 0;
        int createdCount = 0;
        List<String> logs = new ArrayList<>();

        Category defaultCategory = categoryRepository.findById(1L)
                .orElseGet(() -> categoryRepository.findAll().stream().findFirst().orElse(null));

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("--") || line.startsWith("#")) {
                continue;
            }

            String name = "";
            String ingredients = "";

            if (line.contains(":")) {
                int colonIdx = line.indexOf(":");
                name = line.substring(0, colonIdx).trim();
                ingredients = line.substring(colonIdx + 1).trim();
            } else if (line.contains("-")) {
                int hyphenIdx = line.indexOf("-");
                name = line.substring(0, hyphenIdx).trim();
                ingredients = line.substring(hyphenIdx + 1).trim();
            } else {
                logs.add("Dòng " + (i + 1) + " bỏ qua: Không đúng định dạng (thiếu ':' hoặc '-')");
                continue;
            }

            if (name.isEmpty() || ingredients.isEmpty()) {
                logs.add("Dòng " + (i + 1) + " bỏ qua: Tên món ăn hoặc thành phần trống");
                continue;
            }

            Optional<Product> optProduct = productRepository.findByNameIgnoreCase(name);
            Product product;
            if (optProduct.isPresent()) {
                product = optProduct.get();
                product.setIngredients(ingredients);
                if (tenant != null) {
                    product.setTenant(tenant);
                }
                updatedCount++;
                logs.add("Cập nhật: Món '" + product.getName() + "' -> Thành phần: " + ingredients);
            } else {
                product = Product.builder()
                        .name(name)
                        .ingredients(ingredients)
                        .isActive(true)
                        .category(defaultCategory)
                        .tenant(tenant)
                        .build();
                createdCount++;
                logs.add("Tạo mới: Món '" + name + "' -> Thành phần: " + ingredients);
            }
            productRepository.save(product);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("updatedCount", updatedCount);
        result.put("createdCount", createdCount);
        result.put("details", logs);
        return result;
    }
}
