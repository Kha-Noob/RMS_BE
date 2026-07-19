package web.restaurant.swp.modules.inventory.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import web.restaurant.swp.modules.auth.model.*;
import web.restaurant.swp.modules.auth.repository.*;
import web.restaurant.swp.modules.auth.service.*;
import web.restaurant.swp.modules.pos.model.*;
import web.restaurant.swp.modules.pos.repository.*;
import web.restaurant.swp.modules.pos.service.*;
import web.restaurant.swp.modules.inventory.model.*;
import web.restaurant.swp.modules.inventory.repository.*;
import web.restaurant.swp.modules.inventory.service.*;
import web.restaurant.swp.modules.procurement.model.*;
import web.restaurant.swp.modules.procurement.repository.*;
import web.restaurant.swp.modules.procurement.service.*;
import web.restaurant.swp.modules.hr.model.*;
import web.restaurant.swp.modules.hr.repository.*;
import web.restaurant.swp.modules.hr.service.*;
import web.restaurant.swp.modules.loyalty.model.*;
import web.restaurant.swp.modules.loyalty.repository.*;
import web.restaurant.swp.modules.loyalty.service.*;
import web.restaurant.swp.modules.promotion.model.*;
import web.restaurant.swp.modules.promotion.repository.*;
import web.restaurant.swp.modules.promotion.service.*;
import web.restaurant.swp.modules.analytics.service.*;
import web.restaurant.swp.modules.branch.model.*;
import web.restaurant.swp.modules.branch.repository.*;
import web.restaurant.swp.modules.branch.service.BranchAccessService;

import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class InventoryController {

    private final BranchInventoryRepository branchInventoryRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final ProductStockRepository productStockRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BranchRepository branchRepository;
    private final BranchTransferRepository branchTransferRepository;
    private final BranchTransferItemRepository branchTransferItemRepository;
    private final BranchAccessService branchAccessService;
    private final AutonomousInventoryAgent autonomousInventoryAgent;

    private User getLoggedInUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private String getActiveBranchId() {
        return web.restaurant.swp.config.BranchContext.getActiveBranchId(getLoggedInUser());
    }

    private String getActiveTenantId() {
        User user = getLoggedInUser();
        if (user != null && user.getTenant() != null) {
            return user.getTenant().getTenantId();
        }
        return "tenant-1";
    }



    @GetMapping("/api/inventory/stock")
    public ResponseEntity<?> getStock() {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            List<BranchInventory> branchInventories = branchInventoryRepository.findByBranchBranchId(branchId);

            List<Map<String, Object>> result = new ArrayList<>();
            for (BranchInventory bi : branchInventories) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", bi.getItem().getId());
                map.put("name", bi.getItem().getName());
                map.put("unit", bi.getItem().getUnit());
                map.put("currentStock", bi.getQuantity());
                map.put("minimumStock", bi.getItem().getMinimumThreshold());
                map.put("branchName", bi.getBranch().getName());
                result.add(map);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/inventory/adjust")
    public ResponseEntity<?> adjustStock(@RequestBody AdjustStockRequest request) {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            inventoryService.executeStocktake(
                branchId,
                request.getItemId(),
                request.getActualQuantity()
            );
            return ResponseEntity.ok("Successfully adjusted stock");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @lombok.Data
    public static class FrontendRecipeRequest {
        private String name;
        private String description;
        private List<FrontendRecipeIngredient> ingredients;
    }

    @lombok.Data
    public static class FrontendRecipeIngredient {
        private Long rawMaterialId;
        private Double quantity;
        private String unit;
    }

    @GetMapping("/api/inventory/recipes")
    public ResponseEntity<?> getRecipes() {
        try {
            User user = getLoggedInUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            
            List<ProductStock> stocks = productStockRepository.findAll();
            
            // Group by ProductVariant
            Map<ProductVariant, List<ProductStock>> grouped = stocks.stream()
                    .collect(java.util.stream.Collectors.groupingBy(ProductStock::getVariant));
            
            List<Map<String, Object>> response = new java.util.ArrayList<>();
            for (Map.Entry<ProductVariant, List<ProductStock>> entry : grouped.entrySet()) {
                ProductVariant variant = entry.getKey();
                List<ProductStock> portionList = entry.getValue();
                
                Map<String, Object> recipeMap = new java.util.HashMap<>();
                recipeMap.put("id", variant.getId());
                recipeMap.put("name", variant.getProduct() != null ? variant.getProduct().getName() + " (" + variant.getName() + ")" : variant.getName());
                recipeMap.put("description", variant.getProduct() != null ? variant.getProduct().getDescription() : "");
                
                List<Map<String, Object>> ingredientList = new java.util.ArrayList<>();
                for (ProductStock ps : portionList) {
                    Map<String, Object> ingMap = new java.util.HashMap<>();
                    ingMap.put("id", ps.getId());
                    ingMap.put("rawMaterialId", ps.getItem().getId());
                    ingMap.put("rawMaterialName", ps.getItem().getName());
                    ingMap.put("quantity", ps.getQuantityNeeded());
                    ingMap.put("unit", ps.getItem().getUnit());
                    ingredientList.add(ingMap);
                }
                recipeMap.put("ingredients", ingredientList);
                response.add(recipeMap);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/inventory/recipes")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> saveRecipeFromFrontend(@RequestBody FrontendRecipeRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Tên công thức không được trống");
            }
            
            ProductVariant variant = null;
            try {
                Long variantId = Long.parseLong(request.getName().trim());
                variant = productVariantRepository.findById(variantId).orElse(null);
            } catch (NumberFormatException e) {
                // Ignore
            }
            
            if (variant == null) {
                Optional<ProductVariant> pvOpt = productVariantRepository.findBySku(request.getName().trim());
                if (pvOpt.isPresent()) {
                    variant = pvOpt.get();
                }
            }
            
            if (variant == null) {
                Optional<Product> prodOpt = productRepository.findByNameIgnoreCase(request.getName().trim());
                if (prodOpt.isPresent()) {
                    List<ProductVariant> variants = productVariantRepository.findByProductId(prodOpt.get().getId());
                    if (!variants.isEmpty()) {
                        variant = variants.get(0);
                    }
                }
            }
            
            if (variant == null) {
                User user = getLoggedInUser();
                Product product = Product.builder()
                        .name(request.getName().trim())
                        .description(request.getDescription())
                        .tenant(user != null ? user.getTenant() : null)
                        .isActive(true)
                        .build();
                product = productRepository.save(product);
                
                variant = ProductVariant.builder()
                        .product(product)
                        .name("Default")
                        .price(0.0)
                        .originalPrice(0.0)
                        .sku("RECIPE-" + System.currentTimeMillis())
                        .build();
                variant = productVariantRepository.save(variant);
            }
            
            // Delete existing recipe portions for this variant
            List<ProductStock> existing = productStockRepository.findByVariantId(variant.getId());
            productStockRepository.deleteAll(existing);
            
            // Save new recipe portions
            if (request.getIngredients() != null) {
                for (FrontendRecipeIngredient ing : request.getIngredients()) {
                    if (ing.getRawMaterialId() == null || ing.getQuantity() == null || ing.getQuantity() <= 0) {
                        continue;
                    }
                    InventoryItem item = inventoryItemRepository.findById(ing.getRawMaterialId())
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy nguyên liệu có ID: " + ing.getRawMaterialId()));
                    
                    ProductStock ps = ProductStock.builder()
                            .variant(variant)
                            .item(item)
                            .quantityNeeded(ing.getQuantity())
                            .build();
                    productStockRepository.save(ps);
                }
            }
            
            return ResponseEntity.ok("Successfully saved recipe");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/api/inventory/recipes/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteRecipe(@PathVariable Long id) {
        try {
            if (productStockRepository.existsById(id)) {
                productStockRepository.deleteById(id);
            } else {
                List<ProductStock> portions = productStockRepository.findByVariantId(id);
                productStockRepository.deleteAll(portions);
            }
            return ResponseEntity.ok("Successfully deleted recipe");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/inventory/recipes/variant/{variantId}")
    public ResponseEntity<?> getRecipesByVariant(@PathVariable Long variantId) {
        try {
            return ResponseEntity.ok(productStockRepository.findByVariantId(variantId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/inventory/categories")
    public ResponseEntity<?> getCategories() {
        try {
            User user = getLoggedInUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            if (user.getTenant() == null) {
                return ResponseEntity.ok(categoryRepository.findAll());
            }
            return ResponseEntity.ok(categoryRepository.findByTenantTenantId(user.getTenant().getTenantId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/inventory/menu")
    public ResponseEntity<?> getInventoryMenu() {
        try {
            User user = getLoggedInUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            
            List<Product> products;
            if (user.getTenant() == null) {
                products = productRepository.findAll();
            } else {
                products = productRepository.findByTenantTenantId(user.getTenant().getTenantId());
            }
            
            List<Map<String, Object>> response = new java.util.ArrayList<>();
            for (Product p : products) {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", p.getId());
                map.put("name", p.getName());
                map.put("description", p.getDescription());
                map.put("isActive", p.isActive());
                map.put("category", p.getCategory() != null ? Map.of("id", p.getCategory().getId(), "name", p.getCategory().getName()) : null);
                
                List<ProductVariant> pvList = productVariantRepository.findByProductId(p.getId());
                List<Map<String, Object>> variants = new java.util.ArrayList<>();
                double minPrice = 0.0;
                if (!pvList.isEmpty()) {
                    minPrice = pvList.get(0).getPrice();
                }
                for (ProductVariant pv : pvList) {
                    Map<String, Object> vMap = new java.util.HashMap<>();
                    vMap.put("id", pv.getId());
                    vMap.put("name", pv.getName());
                    vMap.put("price", pv.getPrice());
                    vMap.put("sku", pv.getSku());
                    variants.add(vMap);
                }
                map.put("variants", variants);
                map.put("price", minPrice);
                
                response.add(map);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/inventory/transfer")
    public ResponseEntity<?> getBranchTransfers() {
        try {
            User user = getLoggedInUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            
            String activeBranchId = getActiveBranchId();
            List<BranchTransfer> transfers;
            
            if (activeBranchId != null && !activeBranchId.isEmpty()) {
                transfers = branchTransferRepository.findBySourceBranchBranchIdOrTargetBranchBranchId(activeBranchId, activeBranchId);
            } else {
                transfers = branchTransferRepository.findAll();
            }
            
            if (user.getTenant() != null) {
                String tenantId = user.getTenant().getTenantId();
                transfers = transfers.stream()
                        .filter(t -> (t.getSourceBranch() != null && t.getSourceBranch().getTenant() != null && t.getSourceBranch().getTenant().getTenantId().equals(tenantId)) ||
                                     (t.getTargetBranch() != null && t.getTargetBranch().getTenant() != null && t.getTargetBranch().getTenant().getTenantId().equals(tenantId)))
                        .collect(java.util.stream.Collectors.toList());
            }
            
            List<Map<String, Object>> response = new java.util.ArrayList<>();
            for (BranchTransfer t : transfers) {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", t.getId());
                map.put("fromBranch", t.getSourceBranch() != null ? t.getSourceBranch().getName() : "");
                map.put("toBranch", t.getTargetBranch() != null ? t.getTargetBranch().getName() : "");
                map.put("status", t.getStatus());
                map.put("createdAt", t.getRequestDate() != null ? t.getRequestDate().toString() : "");
                response.add(map);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/inventory/recipes/bulk")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> saveRecipeBulk(@RequestBody RecipeBulkRequest request) {
        try {
            ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn"));
            
            // Delete existing product stocks for this variant
            List<ProductStock> existing = productStockRepository.findByVariantId(request.getVariantId());
            productStockRepository.deleteAll(existing);
            
            // Create new ones
            if (request.getPortions() != null) {
                for (RecipePortion portion : request.getPortions()) {
                    if (portion.getItemId() == null || portion.getQuantityNeeded() == null || portion.getQuantityNeeded() <= 0) {
                        continue;
                    }
                    InventoryItem item = inventoryItemRepository.findById(portion.getItemId())
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy nguyên liệu có ID: " + portion.getItemId()));
                    
                    ProductStock ps = ProductStock.builder()
                            .variant(variant)
                            .item(item)
                            .quantityNeeded(portion.getQuantityNeeded())
                            .build();
                    productStockRepository.save(ps);
                }
            }
            return ResponseEntity.ok("Successfully saved recipe portions");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/inventory/items")
    public ResponseEntity<?> getInventoryItems() {
        try {
            User user = getLoggedInUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            if (user.getTenant() == null) {
                return ResponseEntity.ok(inventoryItemRepository.findAll());
            }
            List<InventoryItem> items = inventoryItemRepository.findByTenantTenantId(user.getTenant().getTenantId());
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/inventory/items")
    public ResponseEntity<?> saveInventoryItem(@RequestBody InventoryItemRequest request) {
        try {
            if (request.getSku() == null || request.getSku().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Mã SKU không được trống");
            }
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Tên nguyên liệu không được trống");
            }
            if (request.getUnit() == null || request.getUnit().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Đơn vị tính không được trống");
            }
            
            InventoryItem item;
            if (request.getId() != null) {
                item = inventoryItemRepository.findById(request.getId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy nguyên liệu"));
                item.setSku(request.getSku().trim());
                item.setName(request.getName().trim());
                item.setUnit(request.getUnit().trim());
                item.setMinimumThreshold(request.getMinimumThreshold() != null ? request.getMinimumThreshold() : 0.0);
            } else {
                // Check SKU
                if (inventoryItemRepository.findBySku(request.getSku().trim()).isPresent()) {
                    throw new RuntimeException("Mã SKU nguyên liệu đã tồn tại!");
                }
                item = InventoryItem.builder()
                        .sku(request.getSku().trim())
                        .name(request.getName().trim())
                        .unit(request.getUnit().trim())
                        .minimumThreshold(request.getMinimumThreshold() != null ? request.getMinimumThreshold() : 0.0)
                        .build();
            }
            inventoryItemRepository.save(item);
            
            // Proactively create BranchInventory for the current branch
            BranchAccessService.ErrorHolder branchError = new BranchAccessService.ErrorHolder();
            String activeBranchId = branchAccessService.validateAndGetBranchId(null, branchError);
            if (branchError.hasError()) return branchError.toResponse();

            if (branchInventoryRepository.findByBranchBranchIdAndItemId(activeBranchId, item.getId()).isEmpty()) {
                Branch branch = branchRepository.findById(activeBranchId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy chi nhánh"));
                BranchInventory binv = BranchInventory.builder()
                        .branch(branch)
                        .item(item)
                        .quantity(0.0)
                        .reorderPoint(item.getMinimumThreshold())
                        .build();
                branchInventoryRepository.save(binv);
            }
            
            return ResponseEntity.ok("Successfully saved inventory item");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/api/inventory/items/{id}")
    public ResponseEntity<?> deleteInventoryItem(@PathVariable Long id) {
        try {
            // Delete related product stocks (recipes) first to avoid constraint violation
            List<ProductStock> productStocks = productStockRepository.findAll().stream()
                    .filter(ps -> ps.getItem().getId().equals(id))
                    .toList();
            productStockRepository.deleteAll(productStocks);

            // Delete related branch inventory
            List<BranchInventory> branchInventories = branchInventoryRepository.findAll().stream()
                    .filter(bi -> bi.getItem().getId().equals(id))
                    .toList();
            branchInventoryRepository.deleteAll(branchInventories);

            inventoryItemRepository.deleteById(id);
            return ResponseEntity.ok("Successfully deleted inventory item");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Không thể xóa nguyên liệu này: " + e.getMessage());
        }
    }

    @PostMapping("/api/inventory/categories")
    public ResponseEntity<?> saveCategory(@RequestBody CategoryRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Tên danh mục không được trống");
            }
            Category cat = Category.builder().name(request.getName().trim()).build();
            categoryRepository.save(cat);
            return ResponseEntity.ok("Successfully saved category");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/api/inventory/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        try {
            categoryRepository.deleteById(id);
            return ResponseEntity.ok("Successfully deleted category");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Không thể xóa danh mục này (có thể có món ăn đang thuộc danh mục này)");
        }
    }

    @PostMapping("/api/inventory/menu")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> saveMenu(@RequestBody MenuSaveRequest request) {
        try {
            User user = getLoggedInUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }

            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

            Product product = Product.builder()
                    .name(request.getName().trim())
                    .category(category)
                    .description(request.getDescription())
                    .imagePath("default.png")
                    .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                    .tenant(user.getTenant())
                    .build();
            product = productRepository.save(product);

            if (request.getVariants() != null && !request.getVariants().isEmpty()) {
                for (MenuSaveRequest.VariantRequest vr : request.getVariants()) {
                    ProductVariant variant = ProductVariant.builder()
                            .product(product)
                            .name(vr.getName().trim())
                            .price(vr.getPrice() != null ? vr.getPrice() : 0.0)
                            .sku(vr.getSku() != null && !vr.getSku().trim().isEmpty() ? vr.getSku().trim() : "SKU-" + product.getId() + "-" + System.currentTimeMillis())
                            .isTopping(false)
                            .build();
                    productVariantRepository.save(variant);
                }
            } else {
                ProductVariant variant = ProductVariant.builder()
                        .product(product)
                        .name(product.getName())
                        .price(request.getPrice() != null ? request.getPrice() : 0.0)
                        .sku("SKU-" + product.getId() + "-" + System.currentTimeMillis())
                        .isTopping(false)
                        .build();
                productVariantRepository.save(variant);
            }

            return ResponseEntity.ok("Successfully created menu item");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/api/inventory/menu/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> updateMenu(@PathVariable Long id, @RequestBody MenuSaveRequest request) {
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn"));

            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

            product.setName(request.getName().trim());
            product.setCategory(category);
            product.setDescription(request.getDescription());
            product.setActive(request.getIsActive() != null ? request.getIsActive() : true);
            productRepository.save(product);

            if (request.getVariants() != null) {
                List<ProductVariant> existing = productVariantRepository.findByProductId(product.getId());
                List<Long> keepIds = new ArrayList<>();
                for (MenuSaveRequest.VariantRequest vr : request.getVariants()) {
                    if (vr.getId() != null) {
                        keepIds.add(vr.getId());
                    }
                }
                for (ProductVariant pv : existing) {
                    if (!keepIds.contains(pv.getId())) {
                        productVariantRepository.delete(pv);
                    }
                }

                for (MenuSaveRequest.VariantRequest vr : request.getVariants()) {
                    ProductVariant variant;
                    if (vr.getId() != null) {
                        variant = productVariantRepository.findById(vr.getId())
                                .orElse(new ProductVariant());
                    } else {
                        variant = new ProductVariant();
                        variant.setProduct(product);
                        variant.setTopping(false);
                    }
                    variant.setName(vr.getName().trim());
                    variant.setPrice(vr.getPrice() != null ? vr.getPrice() : 0.0);
                    if (vr.getSku() != null && !vr.getSku().trim().isEmpty()) {
                        variant.setSku(vr.getSku().trim());
                    } else if (variant.getSku() == null) {
                        variant.setSku("SKU-" + product.getId() + "-" + System.currentTimeMillis());
                    }
                    productVariantRepository.save(variant);
                }
            }

            return ResponseEntity.ok("Successfully updated menu item");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/api/inventory/menu/{variantId}")
    public ResponseEntity<?> deleteMenu(@PathVariable Long variantId) {
        try {
            ProductVariant variant = productVariantRepository.findById(variantId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy biến thể"));
            Product product = variant.getProduct();

            List<ProductVariant> otherVariants = productVariantRepository.findByProductId(product.getId());
            productVariantRepository.delete(variant);

            if (otherVariants.size() <= 1) {
                productRepository.delete(product);
            }

            return ResponseEntity.ok("Successfully deleted menu item");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Không thể xóa món ăn này: " + e.getMessage());
        }
    }

    @lombok.Data
    public static class CategoryRequest {
        private String name;
    }

    @lombok.Data
    public static class MenuSaveRequest {
        private String name;
        private String description;
        private Long categoryId;
        private Double price;
        private Boolean isActive = true;
        private List<VariantRequest> variants;

        @lombok.Data
        public static class VariantRequest {
            private Long id;
            private String name;
            private Double price;
            private String sku;
        }
    }

    @lombok.Data
    public static class AdjustStockRequest {
        private Long itemId;
        private Double actualQuantity;
    }

    @lombok.Data
    public static class RecipeRequest {
        private Long variantId;
        private Long itemId;
        private Double quantityNeeded;
    }

    @lombok.Data
    public static class RecipeBulkRequest {
        private Long variantId;
        private List<RecipePortion> portions;
    }

    @lombok.Data
    public static class RecipePortion {
        private Long itemId;
        private Double quantityNeeded;
    }

    @lombok.Data
    public static class InventoryItemRequest {
        private Long id;
        private String sku;
        private String name;
        private String unit;
        private Double minimumThreshold;
    }

    @PostMapping("/api/inventory/transfer/create")
    public ResponseEntity<?> createTransfer(@RequestBody TransferRequest request) {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String targetBranchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            String sourceBranchId = request.getSourceBranchId();
            
            if (sourceBranchId == null || sourceBranchId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Vui lòng chọn chi nhánh gửi");
            }

            // Validate source branch access for non-admin users
            BranchAccessService.ErrorHolder sourceError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(sourceBranchId, sourceError);
            if (sourceError.hasError()) return sourceError.toResponse();

            if (request.getItemIds() == null || request.getItemIds().isEmpty() || request.getQuantities() == null || request.getQuantities().isEmpty()) {
                return ResponseEntity.badRequest().body("Vui lòng thêm ít nhất một nguyên liệu");
            }
            
            BranchTransfer transfer = inventoryService.createTransferRequest(
                sourceBranchId,
                targetBranchId,
                request.getItemIds(),
                request.getQuantities()
            );
            return ResponseEntity.ok(transfer);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Không thể tạo yêu cầu điều chuyển: " + e.getMessage());
        }
    }

    @PostMapping("/api/inventory/transfer/approve/{id}")
    public ResponseEntity<?> approveTransfer(@PathVariable Long id) {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.getLoggedInUser(); // ensures authenticated
            if (branchAccessService.getLoggedInUser() == null) {
                error.set(401, "Not authenticated");
                return error.toResponse();
            }
            
            BranchTransfer transfer = branchTransferRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu điều chuyển"));
            
            String entityBranchId = transfer.getSourceBranch().getBranchId();
            branchAccessService.validateEntityBranch(entityBranchId, error);
            if (error.hasError()) return error.toResponse();
            
            inventoryService.approveAndExecuteTransfer(id);
            return ResponseEntity.ok("Đã phê duyệt và điều chuyển kho thành công");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Không thể phê duyệt yêu cầu: " + e.getMessage());
        }
    }

    @GetMapping("/api/inventory/transfer/details/{id}")
    public ResponseEntity<?> getTransferDetails(@PathVariable Long id) {
        try {
            BranchTransfer transfer = branchTransferRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu điều chuyển"));

            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(transfer.getSourceBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            List<BranchTransferItem> items = branchTransferItemRepository.findByTransferId(id);
            
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("id", transfer.getId());
            response.put("sourceBranchName", transfer.getSourceBranch().getName());
            response.put("targetBranchName", transfer.getTargetBranch().getName());
            response.put("status", transfer.getStatus());
            response.put("requestDate", transfer.getRequestDate().toString());
            response.put("approveDate", transfer.getApproveDate() != null ? transfer.getApproveDate().toString() : "");
            
            List<java.util.Map<String, Object>> itemDetails = new java.util.ArrayList<>();
            for (BranchTransferItem item : items) {
                java.util.Map<String, Object> iMap = new java.util.HashMap<>();
                iMap.put("itemId", item.getItem().getId());
                iMap.put("sku", item.getItem().getSku());
                iMap.put("name", item.getItem().getName());
                iMap.put("unit", item.getItem().getUnit());
                iMap.put("quantity", item.getQuantity());
                itemDetails.add(iMap);
            }
            response.put("items", itemDetails);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Không thể tải chi tiết yêu cầu: " + e.getMessage());
        }
    }

    @lombok.Data
    public static class TransferRequest {
        private String sourceBranchId;
        private List<Long> itemIds;
        private List<Double> quantities;
    }

    @PostMapping("/api/inventory/ai-sync-stock")
    public ResponseEntity<?> syncStockAvailability() {
        try {
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            List<Map<String, Object>> report = autonomousInventoryAgent.syncMenuAvailability(branchId);
            return ResponseEntity.ok(Map.of(
                "message", "Đồng bộ tồn kho thực đơn hoàn tất.",
                "report", report
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi đồng bộ: " + e.getMessage());
        }
    }
}
