package web.restaurant.swp.modules.menu.service;

import web.restaurant.swp.modules.menu.dto.*;

import java.util.List;

public interface MenuService {
    List<MenuItemResponse> getAllMenuItems();
    List<MenuItemResponse> getActiveMenuItems();
    MenuItemResponse getMenuItemById(Long id);
    MenuItemResponse createMenuItem(MenuItemRequest request);
    MenuItemResponse updateMenuItem(Long id, MenuItemRequest request);
    MenuItemResponse patchMenuItemStatus(Long id, String status);
    void deleteMenuItem(Long id);

    List<CategoryResponse> getAllCategories();
    CategoryResponse createCategory(CategoryRequest request);
    CategoryResponse updateCategory(Long id, CategoryRequest request);
    void deleteCategory(Long id);
}
