package web.restaurant.swp.modules.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import web.restaurant.swp.modules.auth.model.*;
import web.restaurant.swp.modules.auth.repository.*;
import web.restaurant.swp.modules.analytics.service.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final UserRepository userRepository;
    private final AIService aiService;

    private User getLoggedInUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private String getActiveBranchId() {
        return web.restaurant.swp.config.BranchContext.getActiveBranchId(getLoggedInUser());
    }

    @PostMapping("/api/analytics/ai-chat")
    public ResponseEntity<?> chatAI(@RequestParam String query) {
        try {
            String branchId = getActiveBranchId();
            String response = aiService.analyzeDailyReport(branchId, query);
            return ResponseEntity.ok(Map.of("reply", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
