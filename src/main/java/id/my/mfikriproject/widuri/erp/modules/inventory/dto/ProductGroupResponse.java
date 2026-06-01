package id.my.mfikriproject.widuri.erp.modules.inventory.dto;

import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductGroupModel;

import java.time.OffsetDateTime;

public record ProductGroupResponse(
        Long id,
        String name,
        String brand,
        String category,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProductGroupResponse from(ProductGroupModel model) {
        return new ProductGroupResponse(
                model.getId(),
                model.getName(),
                model.getBrand(),
                model.getCategory(),
                model.getDescription(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}
