package web.restaurant.swp.modules.menu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.restaurant.swp.modules.menu.dto.*;
import web.restaurant.swp.modules.menu.model.*;
import web.restaurant.swp.modules.menu.repository.*;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuServiceImpl implements MenuService {

    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemVariantRepository menuItemVariantRepository;

    // ─── Menu Items ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getAllMenuItems() {
        List<MenuItem> items = menuItemRepository.findAllWithVariants();
        return items.stream().map(this::toMenuItemResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getActiveMenuItems() {
        List<MenuItem> items = menuItemRepository.findActiveWithVariants();
        return items.stream().map(this::toActiveMenuItemResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemResponse getMenuItemById(Long id) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn với ID: " + id));
        return toMenuItemResponse(item);
    }

    @Override
    @Transactional
    public MenuItemResponse createMenuItem(MenuItemRequest request) {
        MenuCategory category = menuCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

        MenuItem item = MenuItem.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .priceVnd(request.getPriceVnd())
                .imageUrl(request.getImageUrl())
                .category(category)
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .variants(new ArrayList<>())
                .build();
        item = menuItemRepository.save(item);

        if (request.getVariants() != null) {
            for (MenuItemRequest.VariantRequest vr : request.getVariants()) {
                MenuItemVariant variant = MenuItemVariant.builder()
                        .menuItem(item)
                        .name(vr.getName().trim())
                        .priceVnd(vr.getPriceVnd())
                        .active(true)
                        .build();
                item.getVariants().add(variant);
            }
            item = menuItemRepository.save(item);
        }

        log.info("Created menu item: {} (id={})", item.getName(), item.getId());
        return toMenuItemResponse(item);
    }

    @Override
    @Transactional
    public MenuItemResponse updateMenuItem(Long id, MenuItemRequest request) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn với ID: " + id));

        if (request.getCategoryId() != null) {
            MenuCategory category = menuCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));
            item.setCategory(category);
        }

        if (request.getName() != null) item.setName(request.getName().trim());
        if (request.getDescription() != null) item.setDescription(request.getDescription());
        if (request.getPriceVnd() != null) item.setPriceVnd(request.getPriceVnd());
        if (request.getImageUrl() != null) item.setImageUrl(request.getImageUrl());
        if (request.getStatus() != null) item.setStatus(request.getStatus());

        // Replace variants if provided
        if (request.getVariants() != null) {
            item.getVariants().clear();
            for (MenuItemRequest.VariantRequest vr : request.getVariants()) {
                MenuItemVariant variant = MenuItemVariant.builder()
                        .menuItem(item)
                        .name(vr.getName().trim())
                        .priceVnd(vr.getPriceVnd())
                        .active(true)
                        .build();
                item.getVariants().add(variant);
            }
        }

        item = menuItemRepository.save(item);
        log.info("Updated menu item: {} (id={})", item.getName(), item.getId());
        return toMenuItemResponse(item);
    }

    @Override
    @Transactional
    public MenuItemResponse patchMenuItemStatus(Long id, String status) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn với ID: " + id));

        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new RuntimeException("Trạng thái không hợp lệ. Chỉ chấp nhận ACTIVE hoặc INACTIVE.");
        }

        item.setStatus(status);
        item = menuItemRepository.save(item);
        log.info("Patched menu item status: {} -> {} (id={})", item.getName(), status, item.getId());
        return toMenuItemResponse(item);
    }

    @Override
    @Transactional
    public void deleteMenuItem(Long id) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn với ID: " + id));
        menuItemRepository.delete(item);
        log.info("Deleted menu item: {} (id={})", item.getName(), id);
    }

    // ─── Categories ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        List<MenuCategory> categories = menuCategoryRepository.findAllByOrderByDisplayOrderAsc();
        return categories.stream().map(this::toCategoryResponse).toList();
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        MenuCategory category = MenuCategory.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();
        category = menuCategoryRepository.save(category);
        log.info("Created menu category: {} (id={})", category.getName(), category.getId());
        return toCategoryResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        MenuCategory category = menuCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục với ID: " + id));

        if (request.getName() != null) category.setName(request.getName().trim());
        if (request.getDescription() != null) category.setDescription(request.getDescription());
        if (request.getDisplayOrder() != null) category.setDisplayOrder(request.getDisplayOrder());
        if (request.getActive() != null) category.setActive(request.getActive());

        category = menuCategoryRepository.save(category);
        log.info("Updated menu category: {} (id={})", category.getName(), category.getId());
        return toCategoryResponse(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        MenuCategory category = menuCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục với ID: " + id));
        menuCategoryRepository.delete(category);
        log.info("Deleted menu category: {} (id={})", category.getName(), id);
    }

    // ─── Mappers ───────────────────────────────────────────────────────────

    private MenuItemResponse toMenuItemResponse(MenuItem item) {
        List<MenuItemResponse.VariantInfo> variants = item.getVariants() != null
                ? item.getVariants().stream()
                    .filter(v -> v.getActive() != null && v.getActive())
                    .map(v -> MenuItemResponse.VariantInfo.builder()
                            .id(v.getId())
                            .name(v.getName())
                            .priceVnd(v.getPriceVnd())
                            .build())
                    .toList()
                : List.of();

        return MenuItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .priceVnd(item.getPriceVnd())
                .imageUrl(item.getImageUrl())
                .status(item.getStatus())
                .category(item.getCategory() != null
                        ? MenuItemResponse.CategoryInfo.builder()
                                .id(item.getCategory().getId())
                                .name(item.getCategory().getName())
                                .build()
                        : null)
                .variants(variants)
                .build();
    }

    private MenuItemResponse toActiveMenuItemResponse(MenuItem item) {
        List<MenuItemResponse.VariantInfo> variants = item.getVariants() != null
                ? item.getVariants().stream()
                    .filter(v -> v.getActive() != null && v.getActive())
                    .map(v -> MenuItemResponse.VariantInfo.builder()
                            .id(v.getId())
                            .name(v.getName())
                            .priceVnd(v.getPriceVnd())
                            .build())
                    .toList()
                : List.of();

        return MenuItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .priceVnd(item.getPriceVnd())
                .imageUrl(item.getImageUrl())
                .status(item.getStatus())
                .category(item.getCategory() != null
                        ? MenuItemResponse.CategoryInfo.builder()
                                .id(item.getCategory().getId())
                                .name(item.getCategory().getName())
                                .build()
                        : null)
                .variants(variants)
                .build();
    }

    private CategoryResponse toCategoryResponse(MenuCategory category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .displayOrder(category.getDisplayOrder())
                .active(category.getActive())
                .build();
    }
}
