package web.restaurant.swp.modules.review.service;

import web.restaurant.swp.modules.inventory.model.Product;
import web.restaurant.swp.modules.inventory.repository.ProductRepository;
import web.restaurant.swp.modules.loyalty.model.Customer;
import web.restaurant.swp.modules.loyalty.model.LoyaltyTransaction;
import web.restaurant.swp.modules.loyalty.repository.CustomerRepository;
import web.restaurant.swp.modules.loyalty.repository.LoyaltyTransactionRepository;
import web.restaurant.swp.modules.review.model.*;
import web.restaurant.swp.modules.review.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.restaurant.swp.modules.auth.service.S3Service;

import web.restaurant.swp.config.FeedWebSocketHandler;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewFeedService {
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostReportRepository postReportRepository;
    private final BlacklistKeywordRepository blacklistKeywordRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final S3Service s3Service;

    // In-memory rate limiting map (phone -> timestamps list)
    private final java.util.Map<String, List<java.time.LocalDateTime>> postRateLimitMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, List<java.time.LocalDateTime>> commentRateLimitMap = new java.util.concurrent.ConcurrentHashMap<>();

    private String normalizeText(String text) {
        if (text == null) return "";
        // Remove accents (normalize Vietnamese)
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        
        // Remove all non-alphanumeric characters and lowercase it
        return normalized.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private void checkRateLimit(String phone, boolean isPost) {
        if (phone == null || phone.trim().isEmpty()) {
            return;
        }
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime cutoff = now.minusMinutes(5);
        
        java.util.Map<String, List<java.time.LocalDateTime>> limitMap = isPost ? postRateLimitMap : commentRateLimitMap;
        int maxAllowed = isPost ? 3 : 10;
        String actionStr = isPost ? "bài đăng" : "bình luận";
        
        List<java.time.LocalDateTime> times = limitMap.computeIfAbsent(phone, k -> new java.util.ArrayList<>());
        synchronized (times) {
            times.removeIf(t -> t.isBefore(cutoff));
            if (times.size() >= maxAllowed) {
                throw new RuntimeException("Bạn đã vượt quá giới hạn " + maxAllowed + " " + actionStr + " trong 5 phút. Vui lòng thử lại sau.");
            }
            times.add(now);
        }
    }

    @Transactional
    public Post createPost(Post post, List<Long> taggedProductIds) {
        // Rate limiting: Max 3 posts per 5 minutes
        checkRateLimit(post.getAuthorPhone(), true);

        // AC1: Content length validation
        if (post.getContent() == null || post.getContent().trim().isEmpty()) {
            throw new RuntimeException("Nội dung bài viết không được trống.");
        }
        if (post.getContent().length() > 2000) {
            throw new RuntimeException("Nội dung bài viết vượt quá 2000 ký tự.");
        }

        // AC2: Media validation
        if (post.getMediaUrls() != null && !post.getMediaUrls().trim().isEmpty()) {
            String[] urls = post.getMediaUrls().split(";");
            int imgCount = 0;
            int vidCount = 0;
            for (String url : urls) {
                if (url.toLowerCase().endsWith(".mp4") || url.toLowerCase().contains("video")) {
                    vidCount++;
                } else {
                    imgCount++;
                }
            }
            if (vidCount > 1) {
                throw new RuntimeException("Chỉ được tải lên tối đa 1 video.");
            }
            if (imgCount > 5) {
                throw new RuntimeException("Chỉ được tải lên tối đa 5 hình ảnh.");
            }
        }

        // AC3: Blacklist keyword check (Robust normalized matching)
        List<BlacklistKeyword> blacklist = blacklistKeywordRepository.findAll();
        String normalizedContent = normalizeText(post.getContent());
        boolean containsSensitiveWord = false;
        for (BlacklistKeyword bk : blacklist) {
            String normalizedKw = normalizeText(bk.getKeyword());
            if (!normalizedKw.isEmpty() && normalizedContent.contains(normalizedKw)) {
                containsSensitiveWord = true;
                break;
            }
        }
        if (containsSensitiveWord) {
            post.setStatus("PENDING_MODERATION"); // Pending admin approval
        } else {
            post.setStatus("PUBLIC");
        }

        // Tag products from Database Menu
        if (taggedProductIds != null && !taggedProductIds.isEmpty()) {
            List<Product> products = productRepository.findAllById(taggedProductIds);
            post.setTaggedProducts(new HashSet<>(products));
        }

        Post savedPost = postRepository.save(post);

        // Gamification: Loyalty point allocation
        // Criterias: Content length >= 50, at least 1 image/video, status is PUBLIC (approved), author phone exists
        if ("PUBLIC".equals(savedPost.getStatus())
            && savedPost.getAuthorPhone() != null 
            && savedPost.getContent().length() >= 50 
            && savedPost.getMediaUrls() != null 
            && !savedPost.getMediaUrls().trim().isEmpty()) {
            
            // Check daily cap
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(999999999);
            
            List<Post> todayPosts = postRepository.findByStatusOrderByCreatedAtDesc("PUBLIC").stream()
                .filter(p -> p.getAuthorPhone() != null && p.getAuthorPhone().equals(savedPost.getAuthorPhone()))
                .filter(p -> p.getCreatedAt().isAfter(startOfDay) && p.getCreatedAt().isBefore(endOfDay))
                .toList();

            // First post today gets reward
            if (todayPosts.size() <= 1) {
                Optional<Customer> customerOpt = customerRepository.findByPhone(savedPost.getAuthorPhone());
                if (customerOpt.isPresent()) {
                    Customer customer = customerOpt.get();
                    customer.setLoyaltyPoints(customer.getLoyaltyPoints() + 50); // Award 50 points
                    customerRepository.save(customer);

                    LoyaltyTransaction transaction = LoyaltyTransaction.builder()
                            .customer(customer)
                            .points(50)
                            .type("Earn")
                            .transactionDate(LocalDateTime.now())
                            .build();
                    loyaltyTransactionRepository.save(transaction);
                    log.info("[CRM GAMIFICATION] Tặng 50 điểm cho khách hàng {} vì bài đăng chất lượng.", customer.getName());
                }
            }
        }

        if ("PUBLIC".equals(savedPost.getStatus())) {
            FeedWebSocketHandler.broadcast("NEW_POST");
        }
        return savedPost;
    }

    @Transactional
    public Post toggleLike(Long postId, String phone) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));
        
        Optional<PostLike> likeOpt = postLikeRepository.findByPostIdAndAuthorPhone(postId, phone);
        if (likeOpt.isPresent()) {
            postLikeRepository.delete(likeOpt.get());
            post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
        } else {
            PostLike newLike = PostLike.builder()
                .postId(postId)
                .authorPhone(phone)
                .createdAt(LocalDateTime.now())
                .build();
            postLikeRepository.save(newLike);
            post.setLikesCount(post.getLikesCount() + 1);
        }
        Post savedPost = postRepository.save(post);
        FeedWebSocketHandler.broadcast("LIKE_UPDATE:" + savedPost.getId() + ":" + savedPost.getLikesCount());
        return savedPost;
    }

    @Transactional
    public PostComment addComment(Long postId, String authorName, String authorPhone, String content) {
        // Rate limiting: Max 10 comments per 5 minutes
        checkRateLimit(authorPhone, false);

        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("Nội dung bình luận không được trống.");
        }
        postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));
            
        PostComment comment = PostComment.builder()
            .postId(postId)
            .authorName(authorName)
            .authorPhone(authorPhone)
            .content(content)
            .createdAt(LocalDateTime.now())
            .build();
            
        PostComment savedComment = postCommentRepository.save(comment);
        FeedWebSocketHandler.broadcast("COMMENT_UPDATE:" + postId);
        return savedComment;
    }

    @Transactional
    public Post reportPost(Long postId, String reporterPhone, String reason) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));
            
        boolean alreadyReported = postReportRepository.existsByPostIdAndReporterPhone(postId, reporterPhone);
        if (alreadyReported) {
            throw new RuntimeException("Bạn đã báo cáo bài đăng này rồi.");
        }
        
        PostReport report = PostReport.builder()
            .postId(postId)
            .reporterPhone(reporterPhone)
            .reason(reason)
            .createdAt(LocalDateTime.now())
            .build();
        postReportRepository.save(report);
        
        post.setReportCount(post.getReportCount() + 1);
        if (post.getReportCount() >= 3) {
            post.setStatus("PENDING_MODERATION"); // Automatically hide
        }
        Post savedPost = postRepository.save(post);
        if (savedPost.getReportCount() >= 3) {
            FeedWebSocketHandler.broadcast("POST_REMOVED:" + postId);
        }
        return savedPost;
    }

    @Transactional
    public BlacklistKeyword addBlacklistKeyword(String word) {
        if (word == null || word.trim().isEmpty()) {
            throw new RuntimeException("Từ khoá không được trống.");
        }
        String cleanWord = word.trim().toLowerCase();
        if (blacklistKeywordRepository.existsByKeywordIgnoreCase(cleanWord)) {
            throw new RuntimeException("Từ khoá này đã tồn tại trong danh sách đen.");
        }
        BlacklistKeyword bk = BlacklistKeyword.builder()
            .keyword(cleanWord)
            .build();
        return blacklistKeywordRepository.save(bk);
    }

    public List<BlacklistKeyword> getBlacklistKeywords() {
        return blacklistKeywordRepository.findAll();
    }

    @Transactional
    public void deleteBlacklistKeyword(Long id) {
        blacklistKeywordRepository.deleteById(id);
    }

    public List<Post> getPublicFeed() {
        return postRepository.findByStatusOrderByCreatedAtDesc("PUBLIC");
    }
    
    public Page<Post> getPublicFeed(Pageable pageable) {
        return postRepository.findByStatusOrderByCreatedAtDesc("PUBLIC", pageable);
    }

    public List<PostComment> getCommentsForPost(Long postId) {
        return postCommentRepository.findByPostIdOrderByCreatedAtAsc(postId);
    }

    public List<Post> getAdminDashboardPosts() {
        return postRepository.findAll();
    }

    @Transactional
    public Post updatePostStatus(Long postId, String status) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));
        if (!List.of("PUBLIC", "HIDDEN", "PENDING_MODERATION").contains(status)) {
            throw new RuntimeException("Trạng thái không hợp lệ.");
        }
        post.setStatus(status);
        Post savedPost = postRepository.save(post);
        if ("PUBLIC".equals(status)) {
            FeedWebSocketHandler.broadcast("NEW_POST");
        } else {
            FeedWebSocketHandler.broadcast("POST_REMOVED:" + postId);
        }
        return savedPost;
    }
    
    @Transactional
    public void softDeletePost(Long postId, String phone) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));
        if (post.getAuthorPhone() == null || !post.getAuthorPhone().equals(phone)) {
            throw new RuntimeException("Bạn không có quyền xóa bài đăng này.");
        }
        post.setStatus("HIDDEN");
        postRepository.save(post);
        FeedWebSocketHandler.broadcast("POST_REMOVED:" + postId);
    }

    @Transactional
    public void deletePost(Long postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));
        
        // Physical file deletion from S3/local
        if (post.getMediaUrls() != null && !post.getMediaUrls().trim().isEmpty()) {
            String[] urls = post.getMediaUrls().split(";");
            for (String url : urls) {
                if (url != null && !url.trim().isEmpty()) {
                    try {
                        s3Service.deleteFile(url);
                    } catch (Exception e) {
                        log.error("Failed to delete file during hard delete of post " + postId, e);
                    }
                }
            }
        }
        
        postRepository.delete(post);
        FeedWebSocketHandler.broadcast("POST_REMOVED:" + postId);
    }

    @Transactional
    public Post editPost(Long postId, String content, String mediaUrls, Integer rating, String tableCheckIn, String branchId, List<Long> taggedProductIds, String phone) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));
        
        if (post.getAuthorPhone() == null || !post.getAuthorPhone().equals(phone)) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa bài đăng này.");
        }

        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("Nội dung bài viết không được trống.");
        }

        post.setContent(content);
        post.setMediaUrls(mediaUrls);
        if (rating != null) {
            post.setRating(rating);
        }
        post.setTableCheckIn(tableCheckIn);
        post.setBranchId(branchId);

        // Blacklist keyword check (Robust normalized matching)
        List<BlacklistKeyword> blacklist = blacklistKeywordRepository.findAll();
        String normalizedContent = normalizeText(content);
        boolean containsSensitiveWord = false;
        for (BlacklistKeyword bk : blacklist) {
            String normalizedKw = normalizeText(bk.getKeyword());
            if (!normalizedKw.isEmpty() && normalizedContent.contains(normalizedKw)) {
                containsSensitiveWord = true;
                break;
            }
        }
        if (containsSensitiveWord) {
            post.setStatus("PENDING_MODERATION");
        } else {
            post.setStatus("PUBLIC");
        }

        if (taggedProductIds != null) {
            List<Product> products = productRepository.findAllById(taggedProductIds);
            post.setTaggedProducts(new HashSet<>(products));
        }

        post.setIsEdited(true);
        Post savedPost = postRepository.save(post);
        FeedWebSocketHandler.broadcast("NEW_POST"); // Reload feed for clients
        return savedPost;
    }

    @Transactional
    public void deleteComment(Long commentId, String phone) {
        PostComment comment = postCommentRepository.findById(commentId)
            .orElseThrow(() -> new RuntimeException("Comment not found"));
        
        Post post = postRepository.findById(comment.getPostId())
            .orElseThrow(() -> new RuntimeException("Post not found"));
        
        boolean isCommentAuthor = comment.getAuthorPhone() != null && comment.getAuthorPhone().equals(phone);
        boolean isPostAuthor = post.getAuthorPhone() != null && post.getAuthorPhone().equals(phone);
        
        if (!isCommentAuthor && !isPostAuthor) {
            throw new RuntimeException("Bạn không có quyền xóa bình luận này.");
        }
        
        postCommentRepository.delete(comment);
        FeedWebSocketHandler.broadcast("COMMENT_UPDATE:" + post.getId());
    }

    public List<Customer> getLeaderboard() {
        return customerRepository.findTop5ByOrderByLoyaltyPointsDesc();
    }

    public java.util.Map<String, Object> getDashboardStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startOfWeek = now.minusDays(7).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startOfMonth = now.minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);

        List<Post> allPosts = postRepository.findAll();
        
        long postsToday = allPosts.stream().filter(p -> p.getCreatedAt().isAfter(startOfToday)).count();
        long postsWeek = allPosts.stream().filter(p -> p.getCreatedAt().isAfter(startOfWeek)).count();
        long postsMonth = allPosts.stream().filter(p -> p.getCreatedAt().isAfter(startOfMonth)).count();
        
        long pendingCount = allPosts.stream().filter(p -> "PENDING_MODERATION".equals(p.getStatus())).count();
        long reportedCount = allPosts.stream().filter(p -> p.getReportCount() > 0).count();

        // Rating distribution (1 to 5 stars)
        java.util.Map<Integer, Long> ratingDist = allPosts.stream()
            .collect(java.util.stream.Collectors.groupingBy(Post::getRating, java.util.stream.Collectors.counting()));
        for (int i = 1; i <= 5; i++) {
            ratingDist.putIfAbsent(i, 0L);
        }

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("postsToday", postsToday);
        stats.put("postsWeek", postsWeek);
        stats.put("postsMonth", postsMonth);
        stats.put("pendingCount", pendingCount);
        stats.put("reportedCount", reportedCount);
        stats.put("ratingDistribution", ratingDist);
        return stats;
    }

    public List<PostReport> getPostReports(Long postId) {
        return postReportRepository.findByPostId(postId);
    }

    @Transactional
    public Post replyToPost(Long postId, String reply, String adminName) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));
        
        post.setRestaurantReply(reply);
        post.setReplyAuthorName(adminName);
        post.setRepliedAt(LocalDateTime.now());
        
        Post saved = postRepository.save(post);
        FeedWebSocketHandler.broadcast("NEW_POST"); // Broadcast to update feed
        return saved;
    }
}
