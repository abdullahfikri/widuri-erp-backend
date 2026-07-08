package id.my.mfikriproject.widuri.erp.modules.sales.service.impl;

import id.my.mfikriproject.widuri.erp.core.context.StoreContext;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductCostSnapshot;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.StockAdjustRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.ProductService;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.StockAdjustmentService;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutDetailRequest;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutRequest;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.entity.SalesDetailModel;
import id.my.mfikriproject.widuri.erp.modules.sales.entity.SalesModel;
import id.my.mfikriproject.widuri.erp.modules.sales.enums.PaymentMethodEnum;
import id.my.mfikriproject.widuri.erp.modules.sales.repository.SalesDetailRepository;
import id.my.mfikriproject.widuri.erp.modules.sales.repository.SalesRepository;
import id.my.mfikriproject.widuri.erp.modules.sales.service.InvoiceNumberGenerator;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesServiceImplTest {

    @Mock
    private ProductService productService;

    @Mock
    private StockAdjustmentService stockAdjustmentService;

    @Mock
    private InvoiceNumberGenerator invoiceNumberGenerator;

    @Mock
    private SalesRepository salesRepository;

    @Mock
    private SalesDetailRepository salesDetailRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private SalesServiceImpl service;

    private static final int STORE_ID = 1;

    @Test
    void checkout_storeContextNotBound_throwsIllegalStateException() {
        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(1L, 1, new BigDecimal("200.00"))));

        assertThatThrownBy(() -> service.checkout(request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void checkout_soldPriceBelowFloor_throwsAndSkipsStockAndPersistence() {
        given(invoiceNumberGenerator.generate(any(), any())).willReturn("INV-01-20260706-0001");
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("100.00"), new BigDecimal("150.00")));

        // soldPrice 149.99 < floorPrice 150.00
        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(1L, 1, new BigDecimal("149.99"))));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(stockAdjustmentService, never()).adjustOut(any(), any());
        verify(salesRepository, never()).save(any());
        verify(salesDetailRepository, never()).save(any());
    }

    @Test
    void checkout_soldPriceEqualsFloor_isAllowed() {
        stubHappyPath();
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("100.00"), new BigDecimal("150.00")));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(1L, 1, new BigDecimal("150.00"))));

        CheckoutResponse response = ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request));

        assertThat(response.totalAmount()).isEqualByComparingTo("150.00");
        verify(salesRepository).save(any());
    }

    @Test
    void checkout_singleItem_persistsSaleAndReturnsResponse() {
        stubHappyPath();
        given(invoiceNumberGenerator.generate(STORE_ID, LocalDate.now())).willReturn("INV-01-20260706-0001");
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("100.00"), new BigDecimal("150.00")));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.QRIS,
                List.of(new CheckoutDetailRequest(1L, 2, new BigDecimal("200.00"))));

        CheckoutResponse response = ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request));

        assertThat(response.invoiceNumber()).isEqualTo("INV-01-20260706-0001");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethodEnum.QRIS);
        assertThat(response.transactionDate()).isNotNull();
        assertThat(response.totalAmount()).isEqualByComparingTo("400.00"); // 2 * 200.00
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().subtotal()).isEqualByComparingTo("400.00");

        verify(stockAdjustmentService).adjustOut(1L, new StockAdjustRequest(2, "sold"));
        verify(salesRepository).save(any(SalesModel.class));
        verify(salesDetailRepository).save(any(SalesDetailModel.class));
    }

    @Test
    void checkout_multipleItems_totalIsSumOfSubtotalsNotDoubled() {
        stubHappyPath();
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("100.00"), new BigDecimal("150.00")));
        given(productService.getCostSnapshot(2L))
                .willReturn(new ProductCostSnapshot(2L, new BigDecimal("50.00"), new BigDecimal("80.00")));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH, List.of(
                new CheckoutDetailRequest(1L, 2, new BigDecimal("200.00")), // 400.00
                new CheckoutDetailRequest(2L, 3, new BigDecimal("100.00"))  // 300.00
        ));

        CheckoutResponse response = ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request));

        assertThat(response.totalAmount()).isEqualByComparingTo("700.00");
        assertThat(response.items()).hasSize(2);
        verify(salesDetailRepository, times(2)).save(any(SalesDetailModel.class));
    }

    @Test
    void checkout_snapshotsCostPriceAtTimeFromBasePrice() {
        stubHappyPath();
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("123.45"), new BigDecimal("150.00")));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.TRANSFER,
                List.of(new CheckoutDetailRequest(1L, 2, new BigDecimal("200.00"))));

        ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .run(() -> service.checkout(request));

        ArgumentCaptor<SalesDetailModel> captor = ArgumentCaptor.forClass(SalesDetailModel.class);
        verify(salesDetailRepository).save(captor.capture());
        SalesDetailModel detail = captor.getValue();

        assertThat(detail.getCostPriceAtTime()).isEqualByComparingTo("123.45"); // from basePrice, not client
        assertThat(detail.getSoldPriceAtTime()).isEqualByComparingTo("200.00");
        assertThat(detail.getQuantity()).isEqualTo(2);
        assertThat(detail.getSubtotal()).isEqualByComparingTo("400.00");
    }

    @Test
    void checkout_persistsParentWithFinalTotalAndInvoice() {
        stubHappyPath();
        given(invoiceNumberGenerator.generate(STORE_ID, LocalDate.now())).willReturn("INV-01-20260706-0009");
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("100.00"), new BigDecimal("150.00")));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(1L, 2, new BigDecimal("200.00"))));

        ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .run(() -> service.checkout(request));

        ArgumentCaptor<SalesModel> captor = ArgumentCaptor.forClass(SalesModel.class);
        verify(salesRepository).save(captor.capture());
        SalesModel sales = captor.getValue();

        assertThat(sales.getInvoiceNumber()).isEqualTo("INV-01-20260706-0009");
        assertThat(sales.getTotalAmount()).isEqualByComparingTo("400.00");
        assertThat(sales.getPaymentMethod()).isEqualTo(PaymentMethodEnum.CASH);
        assertThat(sales.getTransactionDate()).isNotNull();
    }

    @Test
    void checkout_productNotFound_propagatesAndSkipsPersistence() {
        given(invoiceNumberGenerator.generate(any(), any())).willReturn("INV-01-20260706-0001");
        given(productService.getCostSnapshot(1L))
                .willThrow(new EntityNotFoundException("Product not found"));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(1L, 1, new BigDecimal("200.00"))));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request)))
                .isInstanceOf(EntityNotFoundException.class);

        verify(stockAdjustmentService, never()).adjustOut(any(), any());
        verify(salesRepository, never()).save(any());
        verify(salesDetailRepository, never()).save(any());
    }

    @Test
    void checkout_insufficientStock_propagatesAndSkipsPersistence() {
        given(invoiceNumberGenerator.generate(any(), any())).willReturn("INV-01-20260706-0001");
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("100.00"), new BigDecimal("150.00")));
        given(stockAdjustmentService.adjustOut(any(), any()))
                .willThrow(new IllegalArgumentException("Insufficient stock: 0 available, 1 requested"));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(1L, 1, new BigDecimal("200.00"))));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(salesRepository, never()).save(any());
        verify(salesDetailRepository, never()).save(any());
    }

    @Test
    void checkout_secondItemBelowFloor_persistsNothing() {
        stubHappyPath();
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("100.00"), new BigDecimal("150.00")));
        given(productService.getCostSnapshot(2L))
                .willReturn(new ProductCostSnapshot(2L, new BigDecimal("50.00"), new BigDecimal("80.00")));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH, List.of(
                new CheckoutDetailRequest(1L, 1, new BigDecimal("200.00")), // valid
                new CheckoutDetailRequest(2L, 1, new BigDecimal("79.99"))   // below floor 80.00
        ));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request)))
                .isInstanceOf(IllegalArgumentException.class);

        // No partial sale is persisted; item 1's stock deduction relies on @Transactional rollback
        // (verified end-to-end in SalesServiceCheckoutIntegrationTest).
        verify(salesRepository, never()).save(any());
        verify(salesDetailRepository, never()).save(any());
    }

    @Test
    void checkout_emptyDetails_throwsAndDoesNotGenerateInvoice() {
        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH, List.of());

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(invoiceNumberGenerator, never()).generate(any(), any());
        verify(stockAdjustmentService, never()).adjustOut(any(), any());
        verify(salesRepository, never()).save(any());
    }

    @Test
    void checkout_nullDetails_throwsAndDoesNotGenerateInvoice() {
        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH, null);

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(invoiceNumberGenerator, never()).generate(any(), any());
        verify(stockAdjustmentService, never()).adjustOut(any(), any());
        verify(salesRepository, never()).save(any());
    }

    @Test
    void checkout_invoiceGenerationFails_propagatesAndSkipsAll() {
        given(invoiceNumberGenerator.generate(any(), any()))
                .willThrow(new RuntimeException("Sequence generation failed"));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(1L, 1, new BigDecimal("200.00"))));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request)))
                .isInstanceOf(RuntimeException.class);

        // Invoice generation happens before any product lookup or stock adjustment.
        verify(productService, never()).getCostSnapshot(any());
        verify(stockAdjustmentService, never()).adjustOut(any(), any());
        verify(salesRepository, never()).save(any());
        verify(salesDetailRepository, never()).save(any());
    }

    @Test
    void checkout_secondItemStockFails_persistsNothing() {
        stubHappyPath();
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("100.00"), new BigDecimal("150.00")));
        given(productService.getCostSnapshot(2L))
                .willReturn(new ProductCostSnapshot(2L, new BigDecimal("50.00"), new BigDecimal("80.00")));
        // Item 1 adjustOut succeeds, item 2 adjustOut fails with insufficient stock.
        given(stockAdjustmentService.adjustOut(eq(1L), any())).willReturn(null);
        given(stockAdjustmentService.adjustOut(eq(2L), any()))
                .willThrow(new IllegalArgumentException("Insufficient stock: 3 available, 5 requested"));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH, List.of(
                new CheckoutDetailRequest(1L, 1, new BigDecimal("200.00")),
                new CheckoutDetailRequest(2L, 5, new BigDecimal("100.00"))
        ));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request)))
                .isInstanceOf(IllegalArgumentException.class);

        // No partial sale is persisted — @Transactional rollback is verified end-to-end
        // in SalesServiceCheckoutIntegrationTest.
        verify(salesRepository, never()).save(any());
        verify(salesDetailRepository, never()).save(any());
    }

    @Test
    void checkout_zeroQuantity_throwsAndSkipsAll() {
        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(1L, 0, new BigDecimal("200.00"))));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be positive");

        // Quantity guard fires before any DB interaction.
        verify(productService, never()).getCostSnapshot(any());
        verify(stockAdjustmentService, never()).adjustOut(any(), any());
        verify(salesRepository, never()).save(any());
    }

    @Test
    void checkout_negativeQuantity_throwsAndSkipsAll() {
        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(1L, -1, new BigDecimal("200.00"))));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be positive");

        // Negative quantity would have added stock if unchecked — guard prevents it.
        verify(productService, never()).getCostSnapshot(any());
        verify(stockAdjustmentService, never()).adjustOut(any(), any());
        verify(salesRepository, never()).save(any());
    }

    @Test
    void checkout_detailSaveFailsMidLoop_propagatesException() {
        stubHappyPath();
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("100.00"), new BigDecimal("150.00")));
        given(productService.getCostSnapshot(2L))
                .willReturn(new ProductCostSnapshot(2L, new BigDecimal("50.00"), new BigDecimal("80.00")));

        // Parent save succeeds, first detail save succeeds, second detail save fails.
        // Override stubHappyPath's save stub (LENIENT mode allows this).
        given(salesDetailRepository.save(any(SalesDetailModel.class)))
                .willAnswer(inv -> inv.getArgument(0))
                .willThrow(new RuntimeException("DB error on second detail save"));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH, List.of(
                new CheckoutDetailRequest(1L, 1, new BigDecimal("200.00")),
                new CheckoutDetailRequest(2L, 1, new BigDecimal("100.00"))
        ));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB error on second detail save");

        // Parent was persisted before detail loop.
        verify(salesRepository).save(any(SalesModel.class));
        // Both detail saves were attempted (first succeeded, second threw).
        // @Transactional rollback is verified end-to-end in the integration test.
        verify(salesDetailRepository, times(2)).save(any(SalesDetailModel.class));
    }

    @Test
    void checkout_decimalSubtotalPrecision_totalIsExact() {
        stubHappyPath();
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("100.00"), new BigDecimal("150.00")));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH,
                List.of(new CheckoutDetailRequest(1L, 3, new BigDecimal("199.99")))); // 3 * 199.99

        CheckoutResponse response = ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request));

        assertThat(response.totalAmount()).isEqualByComparingTo("599.97");
        assertThat(response.items().getFirst().subtotal()).isEqualByComparingTo("599.97");
    }

    @Test
    void checkout_multipleItems_itemsPreserveOrderAndContent() {
        stubHappyPath();
        given(productService.getCostSnapshot(1L))
                .willReturn(new ProductCostSnapshot(1L, new BigDecimal("100.00"), new BigDecimal("150.00")));
        given(productService.getCostSnapshot(2L))
                .willReturn(new ProductCostSnapshot(2L, new BigDecimal("50.00"), new BigDecimal("80.00")));

        CheckoutRequest request = new CheckoutRequest(PaymentMethodEnum.CASH, List.of(
                new CheckoutDetailRequest(1L, 2, new BigDecimal("200.00")),
                new CheckoutDetailRequest(2L, 3, new BigDecimal("100.00"))
        ));

        CheckoutResponse response = ScopedValue.where(StoreContext.STORE_ID, STORE_ID)
                .call(() -> service.checkout(request));

        assertThat(response.items()).hasSize(2);

        var first = response.items().get(0);
        assertThat(first.productId()).isEqualTo(1L);
        assertThat(first.quantity()).isEqualTo(2);
        assertThat(first.soldPrice()).isEqualByComparingTo("200.00");
        assertThat(first.subtotal()).isEqualByComparingTo("400.00");

        var second = response.items().get(1);
        assertThat(second.productId()).isEqualTo(2L);
        assertThat(second.quantity()).isEqualTo(3);
        assertThat(second.soldPrice()).isEqualByComparingTo("100.00");
        assertThat(second.subtotal()).isEqualByComparingTo("300.00");
    }

    // --- helpers ---

    private void stubHappyPath() {
        given(invoiceNumberGenerator.generate(any(), any())).willReturn("INV-01-20260706-0001");
        given(salesRepository.save(any(SalesModel.class))).willAnswer(inv -> inv.getArgument(0));
        given(salesDetailRepository.save(any(SalesDetailModel.class))).willAnswer(inv -> inv.getArgument(0));
        given(stockAdjustmentService.adjustOut(any(), any())).willReturn(null);
        given(entityManager.getReference(any(), any())).willReturn(null);
    }
}
