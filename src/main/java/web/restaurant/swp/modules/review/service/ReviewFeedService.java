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

    @Transactional
    public Post createPost(Post post, List<Long> taggedProductIds) {
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

        // AC3: Blacklist keyword check (Case-Insensitive)
        List<BlacklistKeyword> blacklist = blacklistKeywordRepository.findAll();
        String contentLower = post.getContent().toLowerCase();
        boolean containsSensitiveWord = false;
        for (BlacklistKeyword bk : blacklist) {
            if (contentLower.contains(bk.getKeyword().toLowerCase())) {
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
        return postRepository.save(post);
    }

    @Transactional
    public PostComment addComment(Long postId, String authorName, String authorPhone, String content) {
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
            
        return postCommentRepository.save(comment);
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
        return postRepository.save(post);
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
        return postRepository.save(post);
    }
    
    @Transactional
    public void deletePost(Long postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));
        postRepository.delete(post);
    }
}
