package web.restaurant.swp.modules.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.inventory.model.Product;
import web.restaurant.swp.modules.inventory.repository.ProductRepository;
import web.restaurant.swp.modules.analytics.service.AIService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AIAllergenController {

    private final ProductRepository productRepository;
    private final AIService aiService;

    @PostMapping("/allergen-check")
    public ResponseEntity<?> checkAllergens(@RequestBody Map<String, String> payload) {
        String allergenQuery = payload.get("allergens");
        if (allergenQuery == null || allergenQuery.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng cung cấp danh sách thành phần dị ứng."));
        }

        try {
            // Fetch all active products
            List<Product> products = productRepository.findByIsActiveTrue();
            
            // Analyze via Gemini
            String jsonResult = aiService.checkAllergens(products, allergenQuery.trim());
            
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(jsonResult);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi phân tích: " + e.getMessage()));
        }
    }
}
