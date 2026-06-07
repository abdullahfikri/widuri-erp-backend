package id.my.mfikriproject.widuri.erp.modules.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustRequest(
        @NotNull @Min(1) @Max(1_000_000) Integer quantity,
        @NotBlank @Size(max = 500) String reason
) {}