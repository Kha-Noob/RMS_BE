package web.restaurant.swp.modules.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.restaurant.swp.modules.inventory.model.*;
import web.restaurant.swp.modules.inventory.repository.*;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutonomousInventoryAgent {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductStockRepository productStockRepository;
    private final BranchInventoryRepository branchInventoryRepository;

    @Transactional
    public List<Map<String, Object>> syncMenuAvailability(String branchId) {
        log.info("Starting autonomous menu availability sync for branch {}", branchId);
        List<Product> products = productRepository.findAll();
        List<Map<String, Object>> report = new ArrayList<>();

        for (Product product : products) {
            List<ProductVariant> variants = productVariantRepository.findByProductId(product.getId());
            if (variants.isEmpty()) {
                continue;
            }

            boolean isProductAvailable = false;
            StringBuilder reasons = new StringBuilder();
            int totalCheckedVariants = 0;
            int outOfStockVariants = 0;

            for (ProductVariant variant : variants) {
                List<ProductStock> recipe = productStockRepository.findByVariantId(variant.getId());
                if (recipe.isEmpty()) {
                    // No recipe defined, assume always available
                    isProductAvailable = true;
                    continue;
                }

                totalCheckedVariants++;
                boolean isVariantAvailable = true;

                for (ProductStock ps : recipe) {
                    Optional<BranchInventory> invOpt = branchInventoryRepository
                            .findByBranchBranchIdAndItemId(branchId, ps.getItem().getId());

                    double currentQty = invOpt.map(BranchInventory::getQuantity).orElse(0.0);
                    if (currentQty < ps.getQuantityNeeded()) {
                        isVariantAvailable = false;
                        reasons.append("Biến thể [")
                               .append(variant.getName())
                               .append("] thiếu nguyên liệu [")
                               .append(ps.getItem().getName())
                               .append("] (Cần: ")
                               .append(ps.getQuantityNeeded())
                               .append(", Có: ")
                               .append(currentQty)
                               .append("); ");
                        break;
                    }
                }

                if (isVariantAvailable) {
                    isProductAvailable = true;
                } else {
                    outOfStockVariants++;
                }
            }

            boolean oldActive = product.isActive();
            boolean newActive = isProductAvailable;

            if (oldActive != newActive) {
                product.setActive(newActive);
                productRepository.save(product);
                log.info("Autonomous Agent toggled product {} ({}) availability to {}", 
                        product.getId(), product.getName(), newActive);

                Map<String, Object> itemReport = new LinkedHashMap<>();
                itemReport.put("productId", product.getId());
                itemReport.put("productName", product.getName());
                itemReport.put("previousStatus", oldActive ? "AVAILABLE" : "OUT_OF_STOCK");
                itemReport.put("newStatus", newActive ? "AVAILABLE" : "OUT_OF_STOCK");
                itemReport.put("reason", newActive ? "Đã bổ sung nguyên liệu đầy đủ." : reasons.toString());
                report.add(itemReport);
            }
        }

        return report;
    }
}
