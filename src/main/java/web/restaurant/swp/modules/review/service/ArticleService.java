package web.restaurant.swp.modules.review.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.review.model.Article;
import web.restaurant.swp.modules.review.repository.ArticleRepository;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.branch.repository.BranchRepository;
import web.restaurant.swp.modules.branch.model.Branch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    @Transactional
    public Article createArticle(Article article, User user) {
        article.setCreatedAt(LocalDateTime.now());
        article.setUpdatedAt(LocalDateTime.now());
        article.setCreatedBy(user.getEmail());

        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        boolean isManager = user.getRoles().stream().anyMatch(r -> r.getName().equals("MANAGER"));
        boolean isCooperator = user.getRoles().stream().anyMatch(r -> r.getName().equals("COOPERATOR"));

        if (isAdmin) {
            // Admin can set global or local, lock or unlock
            if (article.getIsGlobal() == null) article.setIsGlobal(true);
            if (article.getIsLocked() == null) article.setIsLocked(false);
        } else if (isManager || isCooperator) {
            // Manager and Cooperator can only create local unlocked articles for their own branch/tenant
            article.setIsGlobal(false);
            article.setIsLocked(false);
            article.setBranchId(user.getBranch() != null ? user.getBranch().getBranchId() : null);
        } else {
            throw new RuntimeException("Bạn không có quyền thực hiện tác vụ này.");
        }

        // Validate scheduling
        if ("SCHEDULED".equalsIgnoreCase(article.getStatus())) {
            if (article.getPublishAt() == null || article.getPublishAt().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("Thời gian lên lịch phát hành phải ở tương lai.");
            }
        } else if (article.getStatus() == null) {
            article.setStatus("DRAFT");
        }

        return articleRepository.save(article);
    }

    @Transactional
    public Article updateArticle(Long id, Article details, User user) {
        Article existing = articleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết."));

        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        boolean isManager = user.getRoles().stream().anyMatch(r -> r.getName().equals("MANAGER"));
        boolean isCooperator = user.getRoles().stream().anyMatch(r -> r.getName().equals("COOPERATOR"));

        // Access Control Validation
        if (existing.getIsGlobal() && existing.getIsLocked()) {
            if (!isAdmin) {
                throw new RuntimeException("Bài viết này đã bị khóa áp dụng toàn chuỗi. Chỉ Quản lý chuỗi (Admin) mới có quyền chỉnh sửa.");
            }
        }

        if (isManager || isCooperator) {
            if (isManager) {
                String managerBranchId = user.getBranch() != null ? user.getBranch().getBranchId() : null;
                if (existing.getBranchId() != null && !existing.getBranchId().equals(managerBranchId)) {
                    throw new RuntimeException("Bạn không có quyền chỉnh sửa bài viết của chi nhánh khác.");
                }
            } else { // isCooperator
                if (existing.getCreatedBy() != null && !existing.getCreatedBy().equals(user.getEmail())) {
                    Optional<User> creatorOpt = userRepository.findByEmail(existing.getCreatedBy());
                    if (creatorOpt.isPresent()) {
                        User creator = creatorOpt.get();
                        if (creator.getTenant() == null || user.getTenant() == null || 
                            !creator.getTenant().getTenantId().equals(user.getTenant().getTenantId())) {
                            throw new RuntimeException("Bạn không có quyền chỉnh sửa bài viết của chuỗi/nhà hàng khác.");
                        }
                    }
                }
            }
            if (existing.getIsGlobal()) {
                throw new RuntimeException("Quản lý chi nhánh không thể chỉnh sửa bài viết chung của chuỗi.");
            }
        } else if (!isAdmin) {
            throw new RuntimeException("Bạn không có quyền thực hiện tác vụ này.");
        }

        // Apply updates
        existing.setTitle(details.getTitle());
        existing.setContent(details.getContent());
        existing.setMediaUrls(details.getMediaUrls());
        existing.setUpdatedAt(LocalDateTime.now());

        if (isAdmin) {
            if (details.getIsGlobal() != null) existing.setIsGlobal(details.getIsGlobal());
            if (details.getIsLocked() != null) existing.setIsLocked(details.getIsLocked());
            if (details.getBranchId() != null) existing.setBranchId(details.getBranchId());
        }

        // Handle Status & Scheduling updates
        if (details.getStatus() != null) {
            if ("SCHEDULED".equalsIgnoreCase(details.getStatus())) {
                if (details.getPublishAt() == null || details.getPublishAt().isBefore(LocalDateTime.now())) {
                    throw new RuntimeException("Thời gian lên lịch phát hành phải ở tương lai.");
                }
                existing.setPublishAt(details.getPublishAt());
            } else {
                existing.setPublishAt(null);
            }
            existing.setStatus(details.getStatus());
        }

        return articleRepository.save(existing);
    }

    @Transactional
    public void deleteArticle(Long id, User user) {
        Article existing = articleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài viết."));

        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        boolean isManager = user.getRoles().stream().anyMatch(r -> r.getName().equals("MANAGER"));
        boolean isCooperator = user.getRoles().stream().anyMatch(r -> r.getName().equals("COOPERATOR"));

        // Access Control Validation
        if (existing.getIsGlobal() && existing.getIsLocked()) {
            if (!isAdmin) {
                throw new RuntimeException("Bài viết này đã bị khóa áp dụng toàn chuỗi. Chỉ Quản lý chuỗi (Admin) mới có quyền xóa.");
            }
        }

        if (isManager || isCooperator) {
            if (isManager) {
                String managerBranchId = user.getBranch() != null ? user.getBranch().getBranchId() : null;
                if (existing.getBranchId() != null && !existing.getBranchId().equals(managerBranchId)) {
                    throw new RuntimeException("Bạn không có quyền xóa bài viết của chi nhánh khác.");
                }
            } else { // isCooperator
                if (existing.getCreatedBy() != null && !existing.getCreatedBy().equals(user.getEmail())) {
                    Optional<User> creatorOpt = userRepository.findByEmail(existing.getCreatedBy());
                    if (creatorOpt.isPresent()) {
                        User creator = creatorOpt.get();
                        if (creator.getTenant() == null || user.getTenant() == null || 
                            !creator.getTenant().getTenantId().equals(user.getTenant().getTenantId())) {
                            throw new RuntimeException("Bạn không có quyền xóa bài viết của chuỗi/nhà hàng khác.");
                        }
                    }
                }
            }
            if (existing.getIsGlobal()) {
                throw new RuntimeException("Quản lý chi nhánh không thể xóa bài viết chung của chuỗi.");
            }
        } else if (!isAdmin) {
            throw new RuntimeException("Bạn không có quyền thực hiện tác vụ này.");
        }

        articleRepository.delete(existing);
    }

    public List<Article> getPublicArticles(String branchId) {
        List<Article> result = new ArrayList<>();
        // Global and published articles
        for (Article art : articleRepository.findByIsGlobalTrue()) {
            if ("PUBLISHED".equalsIgnoreCase(art.getStatus())) {
                result.add(art);
            }
        }
        // Local and published articles
        if (branchId != null && !branchId.trim().isEmpty()) {
            for (Article art : articleRepository.findByBranchId(branchId)) {
                if ("PUBLISHED".equalsIgnoreCase(art.getStatus())) {
                    result.add(art);
                }
            }
        }
        // Sort by created date descending
        result.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return result;
    }

    public List<Article> getPublicArticlesForTenant(String tenantId) {
        List<Article> allArticles = articleRepository.findAll();
        List<Article> tenantArticles = new ArrayList<>();
        for (Article art : allArticles) {
            if ("PUBLISHED".equalsIgnoreCase(art.getStatus())) {
                // If it is associated with a branch, check if the branch belongs to the tenant
                if (art.getBranchId() != null) {
                    Optional<Branch> branchOpt = branchRepository.findById(art.getBranchId());
                    if (branchOpt.isPresent() && branchOpt.get().getTenant() != null 
                            && branchOpt.get().getTenant().getTenantId().equals(tenantId)) {
                        tenantArticles.add(art);
                    }
                } else {
                    // If it is global (no branchId), check if it was created by an admin or cooperator of this tenant
                    if (art.getCreatedBy() != null) {
                        Optional<User> creatorOpt = userRepository.findByEmail(art.getCreatedBy());
                        if (creatorOpt.isPresent() && creatorOpt.get().getTenant() != null 
                                && creatorOpt.get().getTenant().getTenantId().equals(tenantId)) {
                            tenantArticles.add(art);
                        }
                    }
                }
            }
        }
        // Sort descending
        tenantArticles.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return tenantArticles;
    }

    public List<Article> getDashboardArticles(User user) {
        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        boolean isManager = user.getRoles().stream().anyMatch(r -> r.getName().equals("MANAGER"));
        boolean isCooperator = user.getRoles().stream().anyMatch(r -> r.getName().equals("COOPERATOR"));

        if (isAdmin) {
            return articleRepository.findAll();
        } else if (isManager || isCooperator) {
            List<Article> result = new ArrayList<>();
            // Include all global articles
            result.addAll(articleRepository.findByIsGlobalTrue());
            if (isManager) {
                // Include local articles of this branch
                String managerBranchId = user.getBranch() != null ? user.getBranch().getBranchId() : null;
                if (managerBranchId != null) {
                    result.addAll(articleRepository.findByBranchId(managerBranchId));
                }
            } else { // isCooperator
                // Include local articles of all branches in their tenant
                String cooperatorTenantId = user.getTenant() != null ? user.getTenant().getTenantId() : null;
                if (cooperatorTenantId != null) {
                    List<Branch> tenantBranches = branchRepository.findByTenantTenantId(cooperatorTenantId);
                    for (Branch b : tenantBranches) {
                        result.addAll(articleRepository.findByBranchId(b.getBranchId()));
                    }
                    // Also include articles created by cooperator that have no branchId (tenant-wide)
                    for (Article art : articleRepository.findAll()) {
                        if (user.getEmail().equals(art.getCreatedBy()) && art.getBranchId() == null && !art.getIsGlobal()) {
                            if (!result.contains(art)) {
                                result.add(art);
                            }
                        }
                    }
                }
            }
            return result;
        } else {
            throw new RuntimeException("Bạn không có quyền truy cập danh sách này.");
        }
    }

    // Cron job checking every 30 seconds for scheduled articles to publish automatically
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void publishScheduledArticles() {
        LocalDateTime now = LocalDateTime.now();
        List<Article> scheduled = articleRepository.findByStatusAndPublishAtBefore("SCHEDULED", now);
        if (!scheduled.isEmpty()) {
            log.info("Found {} scheduled article(s) to publish", scheduled.size());
            for (Article art : scheduled) {
                art.setStatus("PUBLISHED");
                art.setPublishAt(null);
                art.setUpdatedAt(now);
                articleRepository.save(art);
                log.info("Article '{}' (ID: {}) has been automatically published.", art.getTitle(), art.getId());
            }
        }
    }
}
