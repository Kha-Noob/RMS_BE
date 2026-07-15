package web.restaurant.swp.modules.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.menu.model.MenuItem;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByStatusOrderByCreatedAtDesc(String status);

    @Query("SELECT m FROM MenuItem m LEFT JOIN FETCH m.variants v LEFT JOIN FETCH m.category " +
           "WHERE m.status = 'ACTIVE' AND (v IS NULL OR v.active = true) " +
           "ORDER BY m.category.displayOrder ASC, m.name ASC")
    List<MenuItem> findActiveWithVariants();

    @Query("SELECT m FROM MenuItem m LEFT JOIN FETCH m.variants v LEFT JOIN FETCH m.category " +
           "ORDER BY m.category.displayOrder ASC, m.name ASC")
    List<MenuItem> findAllWithVariants();

    List<MenuItem> findByCategoryIdAndStatus(Long categoryId, String status);
}
