package web.restaurant.swp.modules.review.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.review.model.CustomerReview;
import web.restaurant.swp.modules.review.repository.CustomerReviewRepository;
import web.restaurant.swp.modules.review.service.AIReviewAgent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import web.restaurant.swp.modules.branch.repository.BranchRepository;
import web.restaurant.swp.modules.branch.model.Branch;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ReviewAdminController {

    private final CustomerReviewRepository customerReviewRepository;
    private final AIReviewAgent aiReviewAgent;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    private User getCurrentUser() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetails) {
                String email = ((UserDetails) principal).getUsername();
                return userRepository.findByEmail(email).orElse(null);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<?> getAggregatedReviews(
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String source) {
        
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Vui lòng đăng nhập."));
        }

        List<CustomerReview> reviews = customerReviewRepository.findAll();
        List<CustomerReview> filtered = new ArrayList<>();

        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        boolean isCooperator = user.getRoles().stream().anyMatch(r -> r.getName().equals("COOPERATOR"));
        boolean isManager = user.getRoles().stream().anyMatch(r -> r.getName().equals("MANAGER"));
        
        String managerBranchId = user.getBranch() != null ? user.getBranch().getBranchId() : null;
        String cooperatorTenantId = user.getTenant() != null ? user.getTenant().getTenantId() : null;

        List<String> allowedBranchIds = new ArrayList<>();
        if (isCooperator && cooperatorTenantId != null) {
            allowedBranchIds = branchRepository.findByTenantTenantId(cooperatorTenantId).stream()
                    .map(Branch::getBranchId)
                    .collect(Collectors.toList());
        }

        for (CustomerReview r : reviews) {
            // Filter by branch permission:
            // Admin can see everything. Manager can only see reviews of their branch.
            // Cooperator can see reviews of all branches under their tenant.
            if (!isAdmin) {
                if (isManager) {
                    if (r.getBranchId() == null || !r.getBranchId().equals(managerBranchId)) {
                        continue;
                    }
                } else if (isCooperator) {
                    if (r.getBranchId() == null || !allowedBranchIds.contains(r.getBranchId())) {
                        continue;
                    }
                } else {
                    continue;
                }
            }

            // Apply query parameters
            if (branchId != null && !branchId.trim().isEmpty() && !branchId.equals(r.getBranchId())) {
                continue;
            }
            if (rating != null && !rating.equals(r.getRating())) {
                continue;
            }
            if (sentiment != null && !sentiment.trim().isEmpty() && !sentiment.equalsIgnoreCase(r.getSentiment())) {
                continue;
            }
            if (source != null && !source.trim().isEmpty() && !source.equalsIgnoreCase(r.getSource())) {
                continue;
            }

            filtered.add(r);
        }

        // Sort descending by created time
        filtered.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        return ResponseEntity.ok(filtered);
    }

    private boolean checkReviewPermission(CustomerReview review, User user) {
        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        if (isAdmin) return true;

        boolean isManager = user.getRoles().stream().anyMatch(r -> r.getName().equals("MANAGER"));
        boolean isCooperator = user.getRoles().stream().anyMatch(r -> r.getName().equals("COOPERATOR"));

        if (isManager) {
            String managerBranchId = user.getBranch() != null ? user.getBranch().getBranchId() : null;
            return review.getBranchId() != null && review.getBranchId().equals(managerBranchId);
        } else if (isCooperator) {
            String cooperatorTenantId = user.getTenant() != null ? user.getTenant().getTenantId() : null;
            if (review.getBranchId() == null || cooperatorTenantId == null) return false;
            Optional<Branch> bOpt = branchRepository.findById(review.getBranchId());
            return bOpt.isPresent() && bOpt.get().getTenant() != null && 
                   cooperatorTenantId.equals(bOpt.get().getTenant().getTenantId());
        }
        return false;
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveReviewResponse(@PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Vui lòng đăng nhập."));
        }

        Optional<CustomerReview> opt = customerReviewRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CustomerReview review = opt.get();

        if (!checkReviewPermission(review, user)) {
            return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền thực hiện hành động này."));
        }

        review.setIsApproved(true);
        customerReviewRepository.save(review);
        log.info("Manager approved AI response for review ID {}", id);
        return ResponseEntity.ok(Map.of(
            "message", "Phản hồi đánh giá đã được duyệt thành công.",
            "reviewId", review.getId(),
            "isApproved", true
        ));
    }

    @PostMapping("/{id}/reply")
    public ResponseEntity<?> customReplyReview(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Vui lòng đăng nhập."));
        }

        String replyVi = payload.get("responseVi");
        String replyEn = payload.get("responseEn");

        Optional<CustomerReview> opt = customerReviewRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CustomerReview review = opt.get();

        if (!checkReviewPermission(review, user)) {
            return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền thực hiện hành động này."));
        }

        if (replyVi != null) review.setResponseVi(replyVi);
        if (replyEn != null) review.setResponseEn(replyEn);
        review.setIsApproved(true);
        customerReviewRepository.save(review);
        log.info("Manager updated and approved custom reply for review ID {}", id);

        return ResponseEntity.ok(review);
    }

    // Mock endpoint to simulate review aggregation from external sites (Google, Facebook, TripAdvisor)
    @PostMapping("/mock-external")
    public ResponseEntity<?> ingestMockExternalReview(@RequestBody Map<String, Object> payload) {
        try {
            String name = (String) payload.getOrDefault("customerName", "Khách hàng ẩn danh");
            String phone = (String) payload.getOrDefault("customerPhone", "");
            Integer rating = (Integer) payload.get("rating");
            String comment = (String) payload.get("comment");
            String branchId = (String) payload.get("branchId");
            String source = (String) payload.getOrDefault("source", "GOOGLE_MAPS"); // GOOGLE_MAPS, FACEBOOK, TRIPADVISOR

            if (rating == null || rating < 1 || rating > 5) {
                return ResponseEntity.badRequest().body(Map.of("message", "Rating must be between 1 and 5."));
            }
            if (branchId == null || branchId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Branch ID is required."));
            }

            CustomerReview review = CustomerReview.builder()
                    .customerName(name)
                    .customerPhone(phone)
                    .rating(rating)
                    .comment(comment)
                    .branchId(branchId)
                    .source(source.toUpperCase())
                    .isApproved(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            // AI Review Agent automatically processes sentiment & responses on save
            Map<String, Object> resolution = aiReviewAgent.processReviewAndGenerateResolution(review);
            return ResponseEntity.ok(resolution);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mock Ingestion error: " + e.getMessage()));
        }
    }
}
