package web.restaurant.swp.modules.analytics.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.analytics.service.AIChatService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AIChatController {

    private final AIChatService aiChatService;
    private final web.restaurant.swp.modules.review.service.ProfanityFilterService profanityFilterService;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập tin nhắn."));
        }

        if (profanityFilterService.hasProfanity(request.getMessage())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tin nhắn chứa từ ngữ không phù hợp."));
        }

        try {
            String responseText = aiChatService.getChatResponse(
                    request.getMessage().trim(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getTenantId(),
                    request.getHistory()
            );
            return ResponseEntity.ok(Map.of("response", responseText));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi xử lý chatbot: " + e.getMessage()));
        }
    }

    @Data
    public static class ChatRequest {
        private String message;
        private Double latitude;
        private Double longitude;
        private String tenantId;
        private List<HistoryMessage> history;

        @Data
        public static class HistoryMessage {
            private String role; // "user" hoặc "model"
            private String text;
        }
    }
}
