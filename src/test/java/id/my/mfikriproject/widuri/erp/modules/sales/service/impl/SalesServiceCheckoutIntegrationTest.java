package id.my.mfikriproject.widuri.erp.modules.sales.service.impl;

import id.my.mfikriproject.widuri.erp.core.context.StoreContext;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutDetailRequest;
import id.my.mfikriproject.widuri.erp.core.exception.BusinessRuleException;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutRequest;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.enums.PaymentMethodEnum;
import id.my.mfikriproject.widuri.erp.modules.sales.service.SalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for {@link SalesService#checkout} against real PostgreSQL.
 * <p>
 * Proves what unit tests cannot: rows actually land in {@code t_sales}/{@code t_sales_detail},
 * stock is truly decremented, and a mid-checkout failure rolls back the whole transaction
 * (including the earlier item's stock deduction). Container is self-provisioned by
 * Testcontainers + {@code @ServiceConnection} — no manual {@code docker compose up} needed.
 * <p>
 * The class is intentionally NOT {@code @Transactional}: checkout's own {@code @Transactional}
 * must govern commit/rollback so the committed (or rolled-back) state is observable here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
class SalesServiceCheckoutIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    private SalesService salesService;

    @Autowired
    private JdbcClient jdbcClient;

    private static final int STORE_ID = 1;
    // Product 1: floor 150.00, stock 10 | Product 2: floor 80.00, stock 20
    private static final long PRODUCT_1 = 1L;
    private static final long PRODUCT_2 = 2L;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("""
                INSERT INTO m_store (id, name) OVERRIDING SYSTEM VALUE VALUES (1, 'Test Store 1')
                ON CONFLICT (id) DO NOTHING
                """).update();
        jdbcClient.sql("""
                INSERT INTO m_product_group (id, name, brand, category) OVERRIDING SYSTEM VALUE
                VALUES (1, 'Test Group', 'TestBrand', 'TestCat')
                ON CONFLICT (id) DO NOTHING
                """).update();
        insertProduct(PRODUCT_1, "SKU-1", "100.00", "300.00", "150.00", 10);
        insertProduct(PRODUCT_2, "SKU-2", "50.00", "200.00", "80.00", 20);
    }

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM t_sales_detail").update();
        jdbcClient.sql("DELETE FROM t_sales").update();
        jdbcClient.sql("DELETE FROM m_product WHERE id IN (1, 2)").update();
        jdbcClient.sql("DELETE FROM m_product_group WHERE id = 1").update();
        jdbcClient.sql("DELETE FROM sys_invoice_sequence").update();
        jdbcClient.sql("DELETE FROM m_store WHERE id = 1").update();
    }

    @Test
    void checkout_happyPath_persistsAndDecrementsStock() {
        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(PRODUCT_1, 2, new BigDecimal("200.00"))));

        CheckoutResponse response = ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> salesService.checkout(request));

        // Invoice format INV-01-YYYYMMDD-0001
        String expectedDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(response.invoiceNumber()).isEqualTo("INV-01-" + expectedDate + "-0001");

        // t_sales row
        assertThat(count("SELECT count(*) FROM t_sales")).isEqualTo(1L);
        BigDecimal total = jdbcClient.sql("SELECT total_amount FROM t_sales")
                .query(BigDecimal.class).single();
        assertThat(total).isEqualByComparingTo("400.00");
        String paymentMethod = jdbcClient.sql("SELECT payment_method FROM t_sales")
                .query(String.class).single();
        assertThat(paymentMethod).isEqualTo("Cash");

        // t_sales_detail row
        assertThat(count("SELECT count(*) FROM t_sales_detail")).isEqualTo(1L);
        var detail = jdbcClient.sql("""
                        SELECT product_id, quantity, cost_price_at_time, sold_price_at_time, subtotal
                        FROM t_sales_detail
                        """)
                .query((rs, n) -> new Object[]{
                        rs.getLong("product_id"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("cost_price_at_time"),
                        rs.getBigDecimal("sold_price_at_time"),
                        rs.getBigDecimal("subtotal")
                }).single();
        assertThat(detail[0]).isEqualTo(PRODUCT_1);
        assertThat(detail[1]).isEqualTo(2);
        assertThat((BigDecimal) detail[2]).isEqualByComparingTo("100.00"); // cost snapshot from base_price
        assertThat((BigDecimal) detail[3]).isEqualByComparingTo("200.00");
        assertThat((BigDecimal) detail[4]).isEqualByComparingTo("400.00");

        // stock decremented 10 -> 8
        assertThat(stockOf(PRODUCT_1)).isEqualTo(8);
    }

    @Test
    void checkout_secondItemBelowFloor_rollsBackEverything() {
        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH, List.of(
                new CheckoutDetailRequest(PRODUCT_1, 2, new BigDecimal("200.00")), // valid, would deduct stock
                new CheckoutDetailRequest(PRODUCT_2, 1, new BigDecimal("79.99"))    // below floor 80.00 -> fail
        ));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> salesService.checkout(request)))
                .isInstanceOf(BusinessRuleException.class);

        // Nothing persisted...
        assertThat(count("SELECT count(*) FROM t_sales")).isZero();
        assertThat(count("SELECT count(*) FROM t_sales_detail")).isZero();
        // ...and the first item's stock deduction was rolled back (still 10).
        assertThat(stockOf(PRODUCT_1)).isEqualTo(10);
    }

    @Test
    void checkout_secondItemInsufficientStock_rollsBackEverything() {
        // Reduce product 2 stock to 3 so a request for 5 triggers subtractStock failure.
        jdbcClient.sql("UPDATE m_product SET stock_quantity = 3 WHERE id = :id")
                .param("id", PRODUCT_2)
                .update();

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH, List.of(
                new CheckoutDetailRequest(PRODUCT_1, 2, new BigDecimal("200.00")), // valid, stock 10→8 in tx
                new CheckoutDetailRequest(PRODUCT_2, 5, new BigDecimal("100.00"))  // needs 5, only 3 available → fail
        ));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> salesService.checkout(request)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient stock");

        // Nothing persisted — entire transaction rolled back.
        assertThat(count("SELECT count(*) FROM t_sales")).isZero();
        assertThat(count("SELECT count(*) FROM t_sales_detail")).isZero();
        // Critical: item 1's stock deduction (10→8) must be rolled back.
        // This proves adjustOut joins the checkout transaction (not REQUIRES_NEW).
        assertThat(stockOf(PRODUCT_1)).isEqualTo(10);
        // Product 2 stock also untouched (still 3, not further decremented).
        assertThat(stockOf(PRODUCT_2)).isEqualTo(3);
    }

    @Test
    void checkout_duplicateProductId_persistsCorrectly() {
        // Same product in two lines — must not deadlock on PESSIMISTIC_WRITE
        // and must correctly deduct stock cumulatively.
        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH, List.of(
                new CheckoutDetailRequest(PRODUCT_1, 2, new BigDecimal("200.00")), // subtotal 400
                new CheckoutDetailRequest(PRODUCT_1, 3, new BigDecimal("200.00"))  // subtotal 600
        ));

        CheckoutResponse response = ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> salesService.checkout(request));

        assertThat(response.totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.items()).hasSize(2);

        // Two detail rows for the same product.
        assertThat(count("SELECT count(*) FROM t_sales_detail")).isEqualTo(2L);

        var details = jdbcClient.sql("""
                        SELECT quantity, subtotal FROM t_sales_detail ORDER BY id
                        """)
                .query((rs, n) -> new Object[]{
                        rs.getInt("quantity"),
                        rs.getBigDecimal("subtotal")
                }).list();
        assertThat(details).hasSize(2);
        assertThat(details.get(0)).containsExactly(2, new BigDecimal("400.00"));
        assertThat(details.get(1)).containsExactly(3, new BigDecimal("600.00"));

        // Stock decremented cumulatively: 10 - 2 - 3 = 5.
        assertThat(stockOf(PRODUCT_1)).isEqualTo(5);
    }

    @Test
    void checkout_qris_paymentMethodPersistedCorrectly() {
        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.QRIS,
                List.of(new CheckoutDetailRequest(PRODUCT_1, 1, new BigDecimal("200.00"))));

        ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> salesService.checkout(request));

        String persistedMethod = jdbcClient
                .sql("SELECT payment_method FROM t_sales")
                .query(String.class).single();
        assertThat(persistedMethod).isEqualTo("QRIS");
    }

    @Test
    void checkout_transfer_paymentMethodPersistedCorrectly() {
        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.TRANSFER,
                List.of(new CheckoutDetailRequest(PRODUCT_2, 1, new BigDecimal("100.00"))));

        ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> salesService.checkout(request));

        String persistedMethod = jdbcClient
                .sql("SELECT payment_method FROM t_sales")
                .query(String.class).single();
        assertThat(persistedMethod).isEqualTo("Transfer");
    }

    @Test
    void checkout_parentSaveFails_rollsBackStockAndInvoiceSequence() {
        // Pre-insert a row that conflicts with the invoice number checkout will generate.
        // The checkout will: (1) generate seq=1 → INV-01-{date}-0001, (2) adjustOut stock,
        // (3) salesRepository.save() → UNIQUE constraint violation on invoice_number.
        String todayDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String invoice = "INV-01-" + todayDate + "-0001";
        jdbcClient.sql("""
                        INSERT INTO t_sales (store_id, invoice_number, total_amount, payment_method, transaction_date)
                        VALUES (1, :invoice, 0, 'Cash', now())
                        """)
                .param("invoice", invoice)
                .update();

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(PRODUCT_1, 2, new BigDecimal("200.00"))));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> salesService.checkout(request)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Only the pre-inserted row exists — checkout saved nothing.
        assertThat(count("SELECT count(*) FROM t_sales")).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM t_sales_detail")).isZero();
        // Stock deduction was rolled back.
        assertThat(stockOf(PRODUCT_1)).isEqualTo(10);

        // Prove the invoice sequence was also rolled back: clean up the pre-inserted
        // row, then a fresh checkout must still get sequence 1 (not 2).
        jdbcClient.sql("DELETE FROM t_sales WHERE invoice_number = :invoice")
                .param("invoice", invoice)
                .update();

        CheckoutRequest retry = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(PRODUCT_1, 1, new BigDecimal("200.00"))));
        CheckoutResponse retryResponse = ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> salesService.checkout(retry));

        assertThat(retryResponse.invoiceNumber()).isEqualTo(invoice); // seq=1, not seq=2
    }

    // --- helpers ---

    private void insertProduct(long id, String sku, String base, String label, String floor, int stock) {
        jdbcClient.sql("""
                        INSERT INTO m_product
                            (id, product_group_id, store_id, sku, attributes,
                             base_price, label_price, floor_price, stock_quantity)
                        OVERRIDING SYSTEM VALUE
                        VALUES (:id, 1, 1, :sku, '{}'::jsonb,
                                :base, :label, :floor, :stock)
                        """)
                .param("id", id)
                .param("sku", sku)
                .param("base", new BigDecimal(base))
                .param("label", new BigDecimal(label))
                .param("floor", new BigDecimal(floor))
                .param("stock", stock)
                .update();
    }

    private long count(String sql) {
        return jdbcClient.sql(sql).query(Long.class).single();
    }

    private int stockOf(long productId) {
        return jdbcClient.sql("SELECT stock_quantity FROM m_product WHERE id = :id")
                .param("id", productId)
                .query(Integer.class)
                .single();
    }
}
