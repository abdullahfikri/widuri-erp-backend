package id.my.mfikriproject.widuri.erp.modules.sales.dto;

import id.my.mfikriproject.widuri.erp.modules.sales.entity.SalesModel;
import id.my.mfikriproject.widuri.erp.modules.sales.enums.PaymentMethodEnum;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Lightweight DTO for paginated transaction history.
 * Intentionally excludes line items — clients request
 * {@code GET /api/sales/{invoiceNumber}} for full detail.
 */
public record SalesSummaryResponse(
        String invoiceNumber,
        OffsetDateTime transactionDate,
        BigDecimal totalAmount,
        PaymentMethodEnum paymentMethod
) {
    public static SalesSummaryResponse from(SalesModel model) {
        return new SalesSummaryResponse(
                model.getInvoiceNumber(),
                model.getTransactionDate(),
                model.getTotalAmount(),
                model.getPaymentMethod());
    }
}
