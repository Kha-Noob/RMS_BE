package web.restaurant.swp.modules.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.review.model.PostLike;

import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByPostIdAndAuthorPhone(Long postId, String authorPhone);
    void deleteByPostIdAndAuthorPhone(Long postId, String authorPhone);
    boolean existsByPostIdAndAuthorPhone(Long postId, String authorPhone);
}
