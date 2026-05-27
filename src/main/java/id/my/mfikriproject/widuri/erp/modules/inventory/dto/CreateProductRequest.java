package id.my.mfikriproject.widuri.erp.modules.inventory.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Map;

public record CreateProductRequest(
        @NotNull Long productGroupId,
        @NotNull @NotEmpty Map<String, String> attributes,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal basePrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal labelPrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal floorPrice,
        @Min(0) @Max(100_000) Integer stockQuantity,
        @Min(0) @Max(10_000) Integer minStockLevel
) {}
