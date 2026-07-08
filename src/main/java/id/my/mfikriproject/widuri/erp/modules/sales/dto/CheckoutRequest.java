package id.my.mfikriproject.widuri.erp.modules.sales.dto;

import id.my.mfikriproject.widuri.erp.modules.sales.enums.PaymentMethodEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CheckoutRequest (
        @NotNull PaymentMethodEnum paymentMethod,
        @NotNull @NotEmpty List<@Valid CheckoutDetailRequest> details
) {}
