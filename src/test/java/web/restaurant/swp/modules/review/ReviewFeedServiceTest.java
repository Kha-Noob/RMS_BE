package web.restaurant.swp.modules.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import web.restaurant.swp.modules.inventory.model.Product;
import web.restaurant.swp.modules.inventory.repository.ProductRepository;
import web.restaurant.swp.modules.loyalty.model.Customer;
import web.restaurant.swp.modules.loyalty.model.LoyaltyTransaction;
import web.restaurant.swp.modules.loyalty.repository.CustomerRepository;
import web.restaurant.swp.modules.loyalty.repository.LoyaltyTransactionRepository;
import web.restaurant.swp.modules.review.model.*;
import web.restaurant.swp.modules.review.repository.*;
import web.restaurant.swp.modules.review.service.ReviewFeedService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewFeedServiceTest {

    @InjectMocks
    private ReviewFeedService reviewFeedService;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private PostCommentRepository postCommentRepository;

    @Mock
    private PostReportRepository postReportRepository;

    @Mock
    private BlacklistKeywordRepository blacklistKeywordRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private LoyaltyTransactionRepository loyaltyTransactionRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreatePost_ShouldSucceed_WhenValidPost() {
        Post post = Post.builder()
                .authorName("Khách Hàng A")
                .authorPhone("0912345678")
                .content("Món ăn rất ngon, không gian ấm cúng!")
                .rating(5)
                .build();

        when(blacklistKeywordRepository.findAll()).thenReturn(new ArrayList<>());
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Post created = reviewFeedService.createPost(post, Collections.emptyList());

        assertNotNull(created);
        assertEquals("PUBLIC", created.getStatus());
        assertEquals(0, created.getReportCount());
        verify(postRepository, times(1)).save(post);
    }

    @Test
    void testCreatePost_ShouldThrowException_WhenContentLengthExceeds2000() {
        String longText = "a".repeat(2001);
        Post post = Post.builder()
                .authorName("A")
                .content(longText)
                .rating(5)
                .build();

        Exception ex = assertThrows(RuntimeException.class, () -> {
            reviewFeedService.createPost(post, Collections.emptyList());
        });

        assertTrue(ex.getMessage().contains("vượt quá 2000 ký tự"));
        verify(postRepository, never()).save(any());
    }

    @Test
    void testCreatePost_ShouldMarkPending_WhenContentContainsBlacklistWord() {
        Post post = Post.builder()
                .authorName("B")
                .content("Bữa ăn hôm nay rất tệ hại, chửi tục tĩu ở đây")
                .rating(1)
                .build();

        BlacklistKeyword bk = BlacklistKeyword.builder().keyword("tệ hại").build();

        when(blacklistKeywordRepository.findAll()).thenReturn(List.of(bk));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Post created = reviewFeedService.createPost(post, Collections.emptyList());

        assertEquals("PENDING_MODERATION", created.getStatus());
        verify(postRepository, times(1)).save(post);
    }

    @Test
    void testCreatePost_ShouldAwardLoyaltyPoints_WhenQualityPostAndDailyCapAllows() {
        Post post = Post.builder()
                .authorName("C")
                .authorPhone("0900000001")
                // At least 50 characters
                .content("Món ăn rất xuất sắc, cá hồi tươi ngon ngọt nước cực kỳ. Nhân viên nhiệt tình hỗ trợ hết mình.")
                .mediaUrls("image1.jpg;image2.jpg") // At least 1 media
                .rating(5)
                .build();

        Customer customer = Customer.builder()
                .id(1L)
                .name("C")
                .phone("0900000001")
                .loyaltyPoints(100)
                .build();

        when(blacklistKeywordRepository.findAll()).thenReturn(new ArrayList<>());
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });
        when(customerRepository.findByPhone("0900000001")).thenReturn(Optional.of(customer));
        // Return only the current post to simulate first post today
        when(postRepository.findByStatusOrderByCreatedAtDesc("PUBLIC")).thenReturn(List.of(post));

        Post created = reviewFeedService.createPost(post, Collections.emptyList());

        assertEquals("PUBLIC", created.getStatus());
        assertEquals(150, customer.getLoyaltyPoints()); // 100 + 50 points awarded
        verify(customerRepository, times(1)).save(customer);
        verify(loyaltyTransactionRepository, times(1)).save(any(LoyaltyTransaction.class));
    }

    @Test
    void testToggleLike_ShouldAddAndRemoveLikeCorrectly() {
        Post post = Post.builder()
                .id(1L)
                .likesCount(0)
                .build();

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postLikeRepository.findByPostIdAndAuthorPhone(1L, "0900000001")).thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Like
        Post liked = reviewFeedService.toggleLike(1L, "0900000001");
        assertEquals(1, liked.getLikesCount());
        verify(postLikeRepository, times(1)).save(any(PostLike.class));

        // 2. Mock that the like exists now
        PostLike activeLike = PostLike.builder().postId(1L).authorPhone("0900000001").build();
        when(postLikeRepository.findByPostIdAndAuthorPhone(1L, "0900000001")).thenReturn(Optional.of(activeLike));

        // 3. Unlike
        Post unliked = reviewFeedService.toggleLike(1L, "0900000001");
        assertEquals(0, unliked.getLikesCount());
        verify(postLikeRepository, times(1)).delete(activeLike);
    }

    @Test
    void testAddComment_ShouldSaveCommentCorrectly() {
        Post post = Post.builder().id(1L).build();
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postCommentRepository.save(any(PostComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostComment comment = reviewFeedService.addComment(1L, "Người Bình Luận", "0900000002", "Món này ngon thật!");

        assertNotNull(comment);
        assertEquals("Món này ngon thật!", comment.getContent());
        assertEquals("Người Bình Luận", comment.getAuthorName());
        verify(postCommentRepository, times(1)).save(any(PostComment.class));
    }

    @Test
    void testReportPost_ShouldHidePost_WhenReportCountHits3() {
        Post post = Post.builder()
                .id(1L)
                .status("PUBLIC")
                .reportCount(2)
                .build();

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postReportRepository.existsByPostIdAndReporterPhone(1L, "0900000003")).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Post reported = reviewFeedService.reportPost(1L, "0900000003", "Nội dung phản cảm");

        assertEquals(3, reported.getReportCount());
        assertEquals("PENDING_MODERATION", reported.getStatus()); // Auto hidden
        verify(postReportRepository, times(1)).save(any(PostReport.class));
    }
}
