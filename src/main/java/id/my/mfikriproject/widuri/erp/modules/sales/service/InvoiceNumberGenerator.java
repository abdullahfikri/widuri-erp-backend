package id.my.mfikriproject.widuri.erp.modules.sales.service;

import java.time.LocalDate;

public interface InvoiceNumberGenerator {

    /**
     * Generates the next unique invoice number for the given store and date.
     * <p>
     * Format: {@code INV-[STOREID]-[YYYYMMDD]-[SEQ]}, e.g. {@code INV-01-20260616-0042}.
     * The sequence resets daily and is independent per store.
     *
     * <p><b>Side effect:</b> each call permanently increments the {@code (storeId, date)}
     * counter in {@code sys_invoice_sequence}. Do NOT call speculatively (preview/dry-run) —
     * only call inside a confirmed checkout. When invoked within a transaction that later
     * rolls back, the increment is rolled back too (gapless semantics).
     *
     * <p><b>Precondition — tenant isolation:</b> this generator trusts its {@code storeId}
     * argument and performs no store-context check. The caller MUST source {@code storeId}
     * from {@link id.my.mfikriproject.widuri.erp.core.context.StoreContext#STORE_ID}
     * (after {@code StoreContext.assertBound()}), exactly as
     * {@code StockAdjustmentServiceImpl} does. Never pass a {@code storeId} taken directly
     * from untrusted request input — that would allow cross-store sequence tampering.
     *
     * @param storeId positive store ID that exists in {@code m_store}
     * @param date    the transaction date; determines the daily-reset counter
     * @return the formatted invoice number
     * @throws IllegalArgumentException if {@code storeId} is null or non-positive, or {@code date} is null
     */
    String generate(Integer storeId, LocalDate date);
}
