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
public class MenuItemResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal priceVnd;
    private String imageUrl;
    private Integer quantity;
    private String status;
    private CategoryInfo category;
    private List<VariantInfo> variants;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryInfo {
        private Long id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VariantInfo {
        private Long id;
        private String name;
        private BigDecimal priceVnd;
    }
}
