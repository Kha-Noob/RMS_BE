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
import web.restaurant.swp.modules.branch.service.BranchAccessService;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final UserRepository userRepository;
    private final AIService aiService;
    private final BranchAccessService branchAccessService;

    @PostMapping("/api/analytics/ai-chat")
    public ResponseEntity<?> chatAI(@RequestParam String query) {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            String response = aiService.analyzeDailyReport(branchId, query);
            return ResponseEntity.ok(Map.of("reply", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
