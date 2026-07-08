package id.my.mfikriproject.widuri.erp.modules.sales.dto;

import id.my.mfikriproject.widuri.erp.modules.sales.entity.SalesModel;
import id.my.mfikriproject.widuri.erp.modules.sales.enums.PaymentMethodEnum;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full transaction detail including every line item.
 * Returned by {@code GET /api/sales/{invoiceNumber}}.
 */
public record SalesDetailResponse(
        String invoiceNumber,
        OffsetDateTime transactionDate,
        BigDecimal totalAmount,
        PaymentMethodEnum paymentMethod,
        List<SalesLineItemResponse> items
) {
    public static SalesDetailResponse from(SalesModel model) {
        List<SalesLineItemResponse> items = model.getDetails() != null
                ? model.getDetails().stream()
                    .map(SalesLineItemResponse::from)
                    .toList()
                : List.of();

        return new SalesDetailResponse(
                model.getInvoiceNumber(),
                model.getTransactionDate(),
                model.getTotalAmount(),
                model.getPaymentMethod(),
                items);
    }
}
