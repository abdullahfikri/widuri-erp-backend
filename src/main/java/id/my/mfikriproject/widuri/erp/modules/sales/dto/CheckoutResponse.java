package id.my.mfikriproject.widuri.erp.modules.sales.dto;

import id.my.mfikriproject.widuri.erp.modules.sales.enums.PaymentMethodEnum;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record CheckoutResponse(
        String invoiceNumber,
        OffsetDateTime transactionDate,
        PaymentMethodEnum paymentMethod,
        BigDecimal totalAmount,
        List<CheckoutItemResponse> items
) {
}
