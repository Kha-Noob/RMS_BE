package web.restaurant.swp.modules.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.review.model.BlacklistKeyword;

import java.util.Optional;

@Repository
public interface BlacklistKeywordRepository extends JpaRepository<BlacklistKeyword, Long> {
    Optional<BlacklistKeyword> findByKeywordIgnoreCase(String keyword);
    boolean existsByKeywordIgnoreCase(String keyword);
}
