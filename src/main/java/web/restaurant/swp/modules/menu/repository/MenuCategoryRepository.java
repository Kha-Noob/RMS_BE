package web.restaurant.swp.modules.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.menu.model.MenuCategory;

import java.util.List;

@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {
    List<MenuCategory> findByActiveTrueOrderByDisplayOrderAsc();
    List<MenuCategory> findAllByOrderByDisplayOrderAsc();
}
