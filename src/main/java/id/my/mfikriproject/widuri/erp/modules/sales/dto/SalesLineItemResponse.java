package id.my.mfikriproject.widuri.erp.modules.sales.dto;

import id.my.mfikriproject.widuri.erp.modules.sales.entity.SalesDetailModel;

import java.math.BigDecimal;

/**
 * Single line item within a sales transaction.
 */
public record SalesLineItemResponse(
        Long productId,
        String productSku,
        Integer quantity,
        BigDecimal soldPriceAtTime,
        BigDecimal subtotal
) {
    public static SalesLineItemResponse from(SalesDetailModel detail) {
        return new SalesLineItemResponse(
                detail.getProductModel().getId(),
                detail.getProductModel().getSku(),
                detail.getQuantity(),
                detail.getSoldPriceAtTime(),
                detail.getSubtotal());
    }
}
