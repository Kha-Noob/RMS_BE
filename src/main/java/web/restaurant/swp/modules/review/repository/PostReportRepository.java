package web.restaurant.swp.modules.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.review.model.PostReport;

import java.util.Optional;

@Repository
public interface PostReportRepository extends JpaRepository<PostReport, Long> {
    Optional<PostReport> findByPostIdAndReporterPhone(Long postId, String reporterPhone);
    boolean existsByPostIdAndReporterPhone(Long postId, String reporterPhone);
    java.util.List<PostReport> findByPostId(Long postId);
}
