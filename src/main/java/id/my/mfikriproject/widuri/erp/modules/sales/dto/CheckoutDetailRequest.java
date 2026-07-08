package id.my.mfikriproject.widuri.erp.modules.sales.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

// costPriceAtTime, subtotal, dan totalAmount tidak diterima dari client — dihitung server-side
public record CheckoutDetailRequest(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(10_000) Integer quantity,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal soldPrice
) {}
