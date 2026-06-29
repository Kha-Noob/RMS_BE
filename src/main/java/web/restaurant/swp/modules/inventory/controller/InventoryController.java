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

    @PostMapping("/api/inventory/recipes")
    public ResponseEntity<?> saveRecipe(@RequestBody RecipeRequest request) {
        try {
            ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn"));
            InventoryItem item = inventoryItemRepository.findById(request.getItemId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy nguyên liệu"));
            
            // Check if mapping already exists
            Optional<ProductStock> existingOpt = productStockRepository.findByVariantId(request.getVariantId()).stream()
                    .filter(ps -> ps.getItem().getId().equals(request.getItemId()))
                    .findFirst();
            
            ProductStock stock;
            if (existingOpt.isPresent()) {
                stock = existingOpt.get();
                stock.setQuantityNeeded(request.getQuantityNeeded());
            } else {
                stock = ProductStock.builder()
                        .variant(variant)
                        .item(item)
                        .quantityNeeded(request.getQuantityNeeded())
                        .build();
            }
            
            productStockRepository.save(stock);
            return ResponseEntity.ok("Successfully saved recipe portion");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/api/inventory/recipes/{id}")
    public ResponseEntity<?> deleteRecipe(@PathVariable Long id) {
        try {
            productStockRepository.deleteById(id);
            return ResponseEntity.ok("Successfully deleted recipe portion");
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
    public ResponseEntity<?> saveMenu(@RequestBody MenuRequest request) {
        try {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

            Product product;
            ProductVariant variant;

            if (request.getId() != null) {
                // Update existing variant
                variant = productVariantRepository.findById(request.getId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy biến thể"));
                product = variant.getProduct();
                
                product.setName(request.getProductName().trim());
                product.setCategory(category);
                product.setDescription(request.getDescription());
                if (request.getImagePath() != null && !request.getImagePath().trim().isEmpty()) {
                    product.setImagePath(request.getImagePath().trim());
                }
                product.setActive(request.isActive());
                product.setIngredients(request.getIngredients());
                productRepository.save(product);

                variant.setName(request.getVariantName().trim());
                variant.setPrice(request.getPrice());
                variant.setOriginalPrice(request.getOriginalPrice());
                variant.setSku(request.getSku().trim());
                productVariantRepository.save(variant);
            } else {
                // Check if SKU already exists
                if (productVariantRepository.findBySku(request.getSku().trim()).isPresent()) {
                    throw new RuntimeException("Mã SKU đã tồn tại trên hệ thống!");
                }

                // Create new product
                product = Product.builder()
                        .name(request.getProductName().trim())
                        .category(category)
                        .description(request.getDescription())
                        .imagePath(request.getImagePath() != null && !request.getImagePath().trim().isEmpty() ? request.getImagePath().trim() : "default.png")
                        .isActive(request.isActive())
                        .ingredients(request.getIngredients())
                        .build();
                product = productRepository.save(product);

                // Create product variant
                variant = ProductVariant.builder()
                        .product(product)
                        .name(request.getVariantName().trim())
                        .price(request.getPrice())
                        .originalPrice(request.getOriginalPrice())
                        .sku(request.getSku().trim())
                        .isTopping(false)
                        .build();
                productVariantRepository.save(variant);
            }

            return ResponseEntity.ok("Successfully saved menu item");
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

            // Check if there are other variants of the product
            List<ProductVariant> otherVariants = productVariantRepository.findByProductId(product.getId());
            
            // Delete variant
            productVariantRepository.delete(variant);

            // If this was the only variant, delete the product too
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
    public static class MenuRequest {
        private Long id; // Variant ID
        private String productName;
        private Long categoryId;
        private String description;
        private String imagePath;
        private boolean active = true;
        private String ingredients;
        
        private String variantName;
        private Double price;
        private Double originalPrice;
        private String sku;
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
