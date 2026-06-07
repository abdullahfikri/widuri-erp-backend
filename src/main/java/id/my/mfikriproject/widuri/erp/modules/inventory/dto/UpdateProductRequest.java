package id.my.mfikriproject.widuri.erp.modules.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record UpdateProductRequest(
        Map<String, Object> attributes,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal basePrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal labelPrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal floorPrice,
        @Min(0) Integer minStockLevel
) {}
