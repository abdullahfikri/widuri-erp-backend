package id.my.mfikriproject.widuri.erp.modules.sales.service;

import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutRequest;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.SalesDetailResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.SalesSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface SalesService {
    CheckoutResponse checkout(CheckoutRequest request);

    /**
     * Paginated transaction history for the current store.
     * @param from start date inclusive
     * @param to   end date inclusive
     */
    Page<SalesSummaryResponse> getHistory(LocalDate from, LocalDate to, Pageable pageable);

    /**
     * Single transaction detail by invoice number, scoped to the current store.
     * @throws id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException
     *         if no sale with that invoice exists for the current store
     */
    SalesDetailResponse getByInvoiceNumber(String invoiceNumber);
}
