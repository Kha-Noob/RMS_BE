package web.restaurant.swp.modules.review.controller;

import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.review.model.Post;
import web.restaurant.swp.modules.review.service.ReviewFeedService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewFeedController {
    private final ReviewFeedService reviewFeedService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetails) {
                String email = ((UserDetails) principal).getUsername();
                return userRepository.findByEmail(email).orElse(null);
            }
        } catch (Exception e) {
            // Ignore if security context is empty
        }
        return null;
    }

    // DTO for post creation request
    public static class CreatePostRequest {
        public String authorName;
        public String authorPhone;
        public String content;
        public String mediaUrls;
        public Integer rating;
        public String tableCheckIn;
        public String branchId;
        public List<Long> taggedProductIds;
    }

    // DTO for comment request
    public static class CommentRequest {
        public String authorName;
        public String authorPhone;
        public String content;
    }

    // 1. Create post
    @PostMapping("/public/feed/posts")
    public ResponseEntity<?> createPost(@RequestBody CreatePostRequest req) {
        User user = getCurrentUser();
        
        Post post = Post.builder()
                .authorName((user != null) ? user.getName() : req.authorName)
                .authorPhone((user != null) ? user.getPhone() : req.authorPhone)
                .userId((user != null) ? user.getId() : null)
                .content(req.content)
                .mediaUrls(req.mediaUrls)
                .rating(req.rating != null ? req.rating : 5)
                .tableCheckIn(req.tableCheckIn)
                .branchId(req.branchId)
                .build();

        if (post.getAuthorName() == null || post.getAuthorName().trim().isEmpty()) {
            post.setAuthorName("Ẩn danh");
        }

        try {
            Post created = reviewFeedService.createPost(post, req.taggedProductIds);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 2. Read feeds (paginated)
    @GetMapping("/public/feed/posts")
    public ResponseEntity<?> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(reviewFeedService.getPublicFeed(pageable));
    }

    // 3. Toggle Like
    @PostMapping("/public/feed/posts/{id}/like")
    public ResponseEntity<?> toggleLike(
            @PathVariable Long id,
            @RequestParam(required = false) String phone) {
        User user = getCurrentUser();
        String targetPhone = (user != null) ? user.getPhone() : phone;
        if (targetPhone == null || targetPhone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Yêu cầu số điện thoại để thực hiện thích bài viết.");
        }
        try {
            return ResponseEntity.ok(reviewFeedService.toggleLike(id, targetPhone));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 4. Submit Comment
    @PostMapping("/public/feed/posts/{id}/comment")
    public ResponseEntity<?> addComment(
            @PathVariable Long id,
            @RequestBody CommentRequest req) {
        User user = getCurrentUser();
        String name = (user != null) ? user.getName() : req.authorName;
        String phone = (user != null) ? user.getPhone() : req.authorPhone;
        if (name == null || name.trim().isEmpty()) {
            name = "Khách vãng lai";
        }
        try {
            return ResponseEntity.ok(reviewFeedService.addComment(id, name, phone, req.content));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 5. Get comments for post
    @GetMapping("/public/feed/posts/{id}/comments")
    public ResponseEntity<?> getComments(@PathVariable Long id) {
        return ResponseEntity.ok(reviewFeedService.getCommentsForPost(id));
    }

    // 6. Submit Report
    @PostMapping("/public/feed/posts/{id}/report")
    public ResponseEntity<?> reportPost(
            @PathVariable Long id,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String reason) {
        User user = getCurrentUser();
        String reporterPhone = (user != null) ? user.getPhone() : phone;
        if (reporterPhone == null || reporterPhone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Yêu cầu số điện thoại để thực hiện báo cáo.");
        }
        try {
            return ResponseEntity.ok(reviewFeedService.reportPost(id, reporterPhone, reason));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 7. Admin Dashboard Posts List
    @GetMapping("/admin/feed/posts")
    public ResponseEntity<?> getAdminDashboard() {
        return ResponseEntity.ok(reviewFeedService.getAdminDashboardPosts());
    }

    // 8. Admin moderate post status
    @PutMapping("/admin/feed/posts/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        try {
            return ResponseEntity.ok(reviewFeedService.updatePostStatus(id, status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 9. Admin delete post
    @DeleteMapping("/admin/feed/posts/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        try {
            reviewFeedService.deletePost(id);
            return ResponseEntity.ok("Xoá bài đăng thành công.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 10. Admin add keyword to blacklist
    @PostMapping("/admin/feed/blacklist")
    public ResponseEntity<?> addBlacklist(@RequestParam String keyword) {
        try {
            return ResponseEntity.ok(reviewFeedService.addBlacklistKeyword(keyword));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
