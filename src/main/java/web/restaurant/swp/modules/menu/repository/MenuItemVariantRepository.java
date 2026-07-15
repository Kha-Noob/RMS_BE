package web.restaurant.swp.modules.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.menu.model.MenuItemVariant;

import java.util.List;

@Repository
public interface MenuItemVariantRepository extends JpaRepository<MenuItemVariant, Long> {
    List<MenuItemVariant> findByMenuItemIdAndActiveTrue(Long menuItemId);
    List<MenuItemVariant> findByMenuItemId(Long menuItemId);
}
