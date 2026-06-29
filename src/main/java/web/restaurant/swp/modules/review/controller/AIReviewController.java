package web.restaurant.swp.modules.review.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.review.model.CustomerReview;
import web.restaurant.swp.modules.review.service.AIReviewAgent;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/public/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AIReviewController {

    private final AIReviewAgent aiReviewAgent;

    @PostMapping("/submit")
    public ResponseEntity<?> submitReview(@RequestBody Map<String, Object> payload) {
        try {
            String customerName = (String) payload.get("customerName");
            String customerPhone = (String) payload.get("customerPhone");
            Integer rating = (Integer) payload.get("rating");
            String comment = (String) payload.get("comment");
            String branchId = (String) payload.get("branchId");
            
            Number orderIdNum = (Number) payload.get("orderId");
            Long orderId = orderIdNum != null ? orderIdNum.longValue() : null;

            if (customerName == null || customerName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Tên khách hàng không được để trống."));
            }
            if (rating == null || rating < 1 || rating > 5) {
                return ResponseEntity.badRequest().body(Map.of("message", "Đánh giá sao phải từ 1 đến 5."));
            }
            if (branchId == null || branchId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Chi nhánh không hợp lệ."));
            }

            CustomerReview review = CustomerReview.builder()
                    .customerName(customerName.trim())
                    .customerPhone(customerPhone)
                    .rating(rating)
                    .comment(comment)
                    .orderId(orderId)
                    .branchId(branchId.trim())
                    .createdAt(LocalDateTime.now())
                    .build();

            Map<String, Object> resolution = aiReviewAgent.processReviewAndGenerateResolution(review);
            return ResponseEntity.ok(resolution);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi gửi đánh giá: " + e.getMessage()));
        }
    }
}
