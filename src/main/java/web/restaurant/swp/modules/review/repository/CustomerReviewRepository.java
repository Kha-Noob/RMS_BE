package web.restaurant.swp.modules.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.review.model.CustomerReview;
import java.util.List;

@Repository
public interface CustomerReviewRepository extends JpaRepository<CustomerReview, Long> {
    List<CustomerReview> findByBranchIdAndCreatedAtBetween(String branchId, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
