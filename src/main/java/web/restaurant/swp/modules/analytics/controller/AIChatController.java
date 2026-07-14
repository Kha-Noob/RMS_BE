package web.restaurant.swp.modules.analytics.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.analytics.service.AIChatService;

import java.util.Map;

@RestController
@RequestMapping("/api/public/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AIChatController {

    private final AIChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập tin nhắn."));
        }

        try {
            String responseText = aiChatService.getChatResponse(
                    request.getMessage().trim(),
                    request.getLatitude(),
                    request.getLongitude()
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
    }
}
