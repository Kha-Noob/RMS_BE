package web.restaurant.swp.modules.analytics.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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
@RequestMapping("/api/public/ai")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AIImportController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TenantRepository tenantRepository;

    // 1. Paste text import endpoint (plain text body)
    @PostMapping(value = "/import-ingredients", consumes = "text/plain")
    public ResponseEntity<?> importIngredientsFromText(@RequestBody String text) {
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nội dung văn bản không được để trống."));
        }
        try {
            Map<String, Object> result = processImport(text);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error importing ingredients from plain text", e);
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi nhập dữ liệu: " + e.getMessage()));
        }
    }

    // 1b. Paste text import endpoint (JSON request body)
    @PostMapping(value = "/import-ingredients", consumes = "application/json")
    public ResponseEntity<?> importIngredientsFromJson(@RequestBody Map<String, Object> payload) {
        Object textObj = payload.get("text");
        if (textObj == null || textObj.toString().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nội dung văn bản không được để trống."));
        }
        try {
            Map<String, Object> result = processImport(textObj.toString());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error importing ingredients from JSON payload", e);
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi nhập dữ liệu: " + e.getMessage()));
        }
    }

    // 2. File upload import endpoint (multipart/form-data)
    @PostMapping(value = "/import-ingredients", consumes = "multipart/form-data")
    public ResponseEntity<?> importIngredientsFromFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File tải lên không được để trống."));
        }
        try {
            String content = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            Map<String, Object> result = processImport(content);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error importing ingredients from file", e);
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi đọc file: " + e.getMessage()));
        }
    }

    private Map<String, Object> processImport(String content) {
        String[] lines = content.split("\r?\n");
        int updatedCount = 0;
        int createdCount = 0;
        List<String> logs = new ArrayList<>();

        // Load default category and tenant safely
        Category defaultCategory = categoryRepository.findById(1L)
                .orElseGet(() -> categoryRepository.findAll().stream().findFirst().orElse(null));
        Tenant defaultTenant = tenantRepository.findById("tenant-1")
                .orElseGet(() -> tenantRepository.findAll().stream().findFirst().orElse(null));

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
                updatedCount++;
                logs.add("Cập nhật: Món '" + product.getName() + "' -> Thành phần: " + ingredients);
            } else {
                product = Product.builder()
                        .name(name)
                        .ingredients(ingredients)
                        .isActive(true)
                        .category(defaultCategory)
                        .tenant(defaultTenant)
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
