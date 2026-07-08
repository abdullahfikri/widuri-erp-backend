package id.my.mfikriproject.widuri.erp.modules.sales.dto;

import java.math.BigDecimal;

public record CheckoutItemResponse(
        Long productId,
        Integer quantity,
        BigDecimal soldPrice,
        BigDecimal subtotal
) {
}
