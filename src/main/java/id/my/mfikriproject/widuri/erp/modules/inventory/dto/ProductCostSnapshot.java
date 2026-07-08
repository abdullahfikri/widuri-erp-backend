package id.my.mfikriproject.widuri.erp.modules.inventory.dto;

import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductModel;

import java.math.BigDecimal;

// Internal cross-module DTO — carries HPP (basePrice) for sales snapshotting.
// Deliberately NOT exposed via ProductResponse to keep cost price out of the public API.
public record ProductCostSnapshot(
        Long productId,
        BigDecimal basePrice,
        BigDecimal floorPrice
) {
    public static ProductCostSnapshot from(ProductModel model) {
        return new ProductCostSnapshot(
                model.getId(),
                model.getBasePrice(),
                model.getFloorPrice()
        );
    }
}
