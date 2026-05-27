package id.my.mfikriproject.widuri.erp.modules.sales.dto;

import id.my.mfikriproject.widuri.erp.modules.sales.enums.PaymentMethodEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// totalAmount tidak diterima dari client — dihitung server-side sebagai sum(detail.subtotal)
public record CreateSalesRequest(
        @NotNull PaymentMethodEnum paymentMethod,
        @NotNull @NotEmpty List<@Valid SalesDetailRequest> details
) {}
