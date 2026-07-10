package web.restaurant.swp.modules.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.review.model.Article;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findByStatus(String status);
    List<Article> findByBranchId(String branchId);
    List<Article> findByIsGlobalTrue();
    List<Article> findByStatusAndPublishAtBefore(String status, LocalDateTime time);
    List<Article> findByStatusAndBranchId(String status, String branchId);
}
