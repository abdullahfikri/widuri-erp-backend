package id.my.mfikriproject.widuri.erp.modules.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record CreateProductRequest(
        @NotNull Long productGroupId,
        @NotBlank @Size(max = 50) String skuAttribute,
        @Size(max = 50) Map<String, Object> attributes,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal basePrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal labelPrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal floorPrice,
        @Min(0) Integer stockQuantity
) {}