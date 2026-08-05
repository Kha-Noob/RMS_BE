package web.restaurant.swp.modules.review.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.review.model.Post;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    List<Post> findByStatusOrderByCreatedAtDesc(String status);
    Page<Post> findByBranchIdInAndStatusOrderByCreatedAtDesc(List<String> branchIds, String status, Pageable pageable);
    List<Post> findByBranchIdInAndStatusOrderByCreatedAtDesc(List<String> branchIds, String status);
    boolean existsByAuthorPhoneAndTableSessionId(String authorPhone, Long tableSessionId);
    boolean existsByTableSessionId(Long tableSessionId);
    long countByAuthorPhoneAndStatusNot(String authorPhone, String status);
}


