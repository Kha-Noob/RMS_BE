package web.restaurant.swp.modules.menu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.restaurant.swp.modules.menu.dto.*;
import web.restaurant.swp.modules.inventory.model.Product;
import web.restaurant.swp.modules.inventory.model.ProductVariant;
import web.restaurant.swp.modules.inventory.model.Category;
import web.restaurant.swp.modules.inventory.repository.ProductRepository;
import web.restaurant.swp.modules.inventory.repository.ProductVariantRepository;
import web.restaurant.swp.modules.inventory.repository.CategoryRepository;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuServiceImpl implements MenuService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    private User getLoggedInUser() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
            String email = ((org.springframework.security.core.userdetails.UserDetails) auth.getPrincipal()).getUsername();
            return userRepository.findByEmail(email).orElse(null);
        }
        return null;
    }

    // ─── Menu Items ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getAllMenuItems() {
        List<Product> products = productRepository.findAll();
        return products.stream().map(this::toMenuItemResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getActiveMenuItems() {
        List<Product> products = productRepository.findByIsActiveTrue();
        return products.stream().map(this::toMenuItemResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MenuItemResponse getMenuItemById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn với ID: " + id));
        return toMenuItemResponse(product);
    }

    @Override
    @Transactional
    public MenuItemResponse createMenuItem(MenuItemRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

        User user = getLoggedInUser();

        Product product = Product.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .imagePath(request.getImageUrl() != null && !request.getImageUrl().trim().isEmpty() ? request.getImageUrl().trim() : "default.png")
                .isActive(request.getStatus() == null || "ACTIVE".equalsIgnoreCase(request.getStatus()))
                .category(category)
                .tenant(user != null ? user.getTenant() : null)
                .build();
        product = productRepository.save(product);

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            for (MenuItemRequest.VariantRequest vr : request.getVariants()) {
                ProductVariant variant = ProductVariant.builder()
                        .product(product)
                        .name(vr.getName().trim())
                        .price(vr.getPriceVnd() != null ? vr.getPriceVnd().doubleValue() : 0.0)
                        .sku("SKU-" + product.getId() + "-" + System.currentTimeMillis())
                        .isTopping(false)
                        .build();
                productVariantRepository.save(variant);
            }
        } else {
            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .name(product.getName())
                    .price(request.getPriceVnd() != null ? request.getPriceVnd().doubleValue() : 0.0)
                    .sku("SKU-" + product.getId() + "-" + System.currentTimeMillis())
                    .isTopping(false)
                    .build();
            productVariantRepository.save(variant);
        }

        log.info("Created menu item in products: {} (id={})", product.getName(), product.getId());
        return toMenuItemResponse(product);
    }

    @Override
    @Transactional
    public MenuItemResponse updateMenuItem(Long id, MenuItemRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn với ID: " + id));

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));
            product.setCategory(category);
        }

        if (request.getName() != null) product.setName(request.getName().trim());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getImageUrl() != null) product.setImagePath(request.getImageUrl().trim());
        if (request.getStatus() != null) product.setActive("ACTIVE".equalsIgnoreCase(request.getStatus()));

        product = productRepository.save(product);

        if (request.getVariants() != null) {
            List<ProductVariant> existing = productVariantRepository.findByProductId(product.getId());
            for (ProductVariant pv : existing) {
                try {
                    productVariantRepository.delete(pv);
                } catch (Exception ex) {
                    log.warn("Cannot delete variant {} due to references", pv.getId());
                }
            }
            for (MenuItemRequest.VariantRequest vr : request.getVariants()) {
                ProductVariant variant = ProductVariant.builder()
                        .product(product)
                        .name(vr.getName().trim())
                        .price(vr.getPriceVnd() != null ? vr.getPriceVnd().doubleValue() : 0.0)
                        .sku("SKU-" + product.getId() + "-" + System.currentTimeMillis())
                        .isTopping(false)
                        .build();
                productVariantRepository.save(variant);
            }
        }

        log.info("Updated menu item in products: {} (id={})", product.getName(), product.getId());
        return toMenuItemResponse(product);
    }

    @Override
    @Transactional
    public MenuItemResponse patchMenuItemStatus(Long id, String status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn với ID: " + id));

        if (!"ACTIVE".equalsIgnoreCase(status) && !"INACTIVE".equalsIgnoreCase(status)) {
            throw new RuntimeException("Trạng thái không hợp lệ. Chỉ chấp nhận ACTIVE hoặc INACTIVE.");
        }

        product.setActive("ACTIVE".equalsIgnoreCase(status));
        product = productRepository.save(product);
        log.info("Patched product status: {} -> {} (id={})", product.getName(), status, product.getId());
        return toMenuItemResponse(product);
    }

    @Override
    @Transactional
    public void deleteMenuItem(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn với ID: " + id));
        List<ProductVariant> vars = productVariantRepository.findByProductId(product.getId());
        for (ProductVariant pv : vars) {
            try {
                productVariantRepository.delete(pv);
            } catch (Exception ex) {
                log.warn("Cannot delete variant: {}", pv.getId());
            }
        }
        try {
            productRepository.delete(product);
        } catch (Exception ex) {
            product.setActive(false);
            productRepository.save(product);
        }
        log.info("Deleted product: {} (id={})", product.getName(), id);
    }

    // ─── Categories ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();
        return categories.stream().map(this::toCategoryResponse).toList();
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = Category.builder()
                .name(request.getName().trim())
                .build();
        category = categoryRepository.save(category);
        log.info("Created category: {} (id={})", category.getName(), category.getId());
        return toCategoryResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục với ID: " + id));

        if (request.getName() != null) category.setName(request.getName().trim());

        category = categoryRepository.save(category);
        log.info("Updated category: {} (id={})", category.getName(), category.getId());
        return toCategoryResponse(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục với ID: " + id));
        categoryRepository.delete(category);
        log.info("Deleted category: {} (id={})", category.getName(), id);
    }

    // ─── Mappers ───────────────────────────────────────────────────────────

    private MenuItemResponse toMenuItemResponse(Product product) {
        List<ProductVariant> vars = productVariantRepository.findByProductId(product.getId());
        BigDecimal priceVnd = vars.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(vars.get(0).getPrice());

        List<MenuItemResponse.VariantInfo> variants = vars.stream()
                .map(v -> MenuItemResponse.VariantInfo.builder()
                        .id(v.getId())
                        .name(v.getName())
                        .priceVnd(BigDecimal.valueOf(v.getPrice()))
                        .build())
                .toList();

        return MenuItemResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .priceVnd(priceVnd)
                .imageUrl(product.getImagePath())
                .status(product.isActive() ? "ACTIVE" : "INACTIVE")
                .category(product.getCategory() != null
                        ? MenuItemResponse.CategoryInfo.builder()
                                .id(product.getCategory().getId())
                                .name(product.getCategory().getName())
                                .build()
                        : null)
                .variants(variants)
                .build();
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description("")
                .displayOrder(0)
                .active(true)
                .build();
    }
}
