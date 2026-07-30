package web.restaurant.swp.modules.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItemRequest {
    private String name;
    private String description;
    private BigDecimal priceVnd;
    private String imageUrl;
    private Long categoryId;
    private CategoryObj category;
    private String status;
    private List<VariantRequest> variants;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryObj {
        private Long id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VariantRequest {
        private Long id;
        private String name;
        private BigDecimal priceVnd;
    }

    public Long getResolvedCategoryId() {
        if (categoryId != null) return categoryId;
        if (category != null && category.getId() != null) return category.getId();
        return null;
    }
}
