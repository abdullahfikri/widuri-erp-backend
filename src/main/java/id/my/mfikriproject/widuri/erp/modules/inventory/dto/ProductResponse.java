package id.my.mfikriproject.widuri.erp.modules.inventory.dto;

import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductModel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record ProductResponse(
        Long id,
        Long productGroupId,
        String sku,
        Map<String, Object> attributes,
        BigDecimal labelPrice,
        BigDecimal floorPrice,
        Integer stockQuantity,
        Integer minStockLevel,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProductResponse from(ProductModel model) {
        return new ProductResponse(
                model.getId(),
                model.getProductGroupModel().getId(),
                model.getSku(),
                model.getAttributes(),
                model.getLabelPrice(),
                model.getFloorPrice(),
                model.getStockQuantity(),
                model.getMinStockLevel(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}
