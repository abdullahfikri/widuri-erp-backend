package id.my.mfikriproject.widuri.erp.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductGroupRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 100) String brand,
        @Size(max = 50) String category,
        @Size(max = 2000) String description
) {}
