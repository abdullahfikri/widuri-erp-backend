package id.my.mfikriproject.widuri.erp.modules.sales.controller;

import id.my.mfikriproject.widuri.erp.core.config.WebMvcConfig;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.SalesDetailResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.SalesLineItemResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.SalesSummaryResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.enums.PaymentMethodEnum;
import id.my.mfikriproject.widuri.erp.modules.sales.service.SalesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebMvcTest(SalesController.class)
@Import(WebMvcConfig.class)
class SalesControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SalesService salesService;

    private static final String URL = "/api/sales";
    private static final String STORE_ID_HEADER = "X-Store-Id";

    // ── POST /checkout ──────────────────────────────────────

    @Test
    void checkout_validRequest_returns201Created() {
        CheckoutResponse response = CheckoutResponse.builder()
                .invoiceNumber("INV-01-20260708-0001")
                .transactionDate(OffsetDateTime.now())
                .paymentMethod(PaymentMethodEnum.QRIS)
                .totalAmount(new BigDecimal("400.00"))
                .items(List.of())
                .build();
        given(salesService.checkout(any())).willReturn(response);

        MvcTestResult result = mvc.post()
                .uri(URL + "/checkout")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentMethod":"QRIS","details":[{"productId":1,"quantity":2,"soldPrice":200.00}]}""")
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson()
                .extractingPath("$.invoiceNumber").asString().isEqualTo("INV-01-20260708-0001");
        assertThat(result).bodyJson()
                .extractingPath("$.totalAmount").asNumber().isEqualTo(400.00);
    }

    @Test
    void checkout_missingStoreIdHeader_returns400() {
        assertThat(mvc.post()
                .uri(URL + "/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentMethod":"CASH","details":[{"productId":1,"quantity":1,"soldPrice":100.00}]}"""))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("MISSING_STORE_ID");
    }

    // ── GET / (history list) ────────────────────────────────

    @Test
    void getHistory_validRequest_returns200WithPage() {
        SalesSummaryResponse summary = new SalesSummaryResponse(
                "INV-01-20260708-0001", OffsetDateTime.now(),
                new BigDecimal("400.00"), PaymentMethodEnum.CASH);
        Page<SalesSummaryResponse> page = new PageImpl<>(List.of(summary));
        given(salesService.getHistory(any(), any(), any())).willReturn(page);

        MvcTestResult result = mvc.get()
                .uri(URL + "?from=2026-07-01&to=2026-07-08")
                .header(STORE_ID_HEADER, "1")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson()
                .extractingPath("$.content").asArray().hasSize(1);
        assertThat(result).bodyJson()
                .extractingPath("$.content[0].invoiceNumber").asString()
                .isEqualTo("INV-01-20260708-0001");
        assertThat(result).bodyJson()
                .extractingPath("$.pagination.page").asNumber().isEqualTo(0);
        assertThat(result).bodyJson()
                .extractingPath("$.pagination.size").asNumber().isEqualTo(1);
        assertThat(result).bodyJson()
                .extractingPath("$.pagination.totalElements").asNumber().isEqualTo(1);
        assertThat(result).bodyJson()
                .extractingPath("$.pagination.totalPages").asNumber().isEqualTo(1);
        assertThat(result).bodyJson()
                .extractingPath("$.pagination.first").asBoolean().isTrue();
        assertThat(result).bodyJson()
                .extractingPath("$.pagination.last").asBoolean().isTrue();
    }

    @Test
    void getHistory_missingStoreIdHeader_returns400() {
        assertThat(mvc.get().uri(URL + "?from=2026-07-01&to=2026-07-08"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("MISSING_STORE_ID");
    }

    @Test
    void getHistory_usesDefaultPagination() {
        given(salesService.getHistory(any(), any(), any())).willReturn(Page.empty());

        mvc.get()
                .uri(URL + "?from=2026-07-01&to=2026-07-08")
                .header(STORE_ID_HEADER, "1")
                .exchange();

        verify(salesService).getHistory(
                eq(LocalDate.of(2026, 7, 1)),
                eq(LocalDate.of(2026, 7, 8)),
                any());
    }

    // ── GET /{invoiceNumber} (detail) ───────────────────────

    @Test
    void getByInvoiceNumber_found_returns200WithDetail() {
        SalesDetailResponse detail = new SalesDetailResponse(
                "INV-01-20260708-0042", OffsetDateTime.now(),
                new BigDecimal("999.99"), PaymentMethodEnum.TRANSFER,
                List.of(new SalesLineItemResponse(1L, "SKU-1", 2,
                        new BigDecimal("500.00"), new BigDecimal("1000.00"))));
        given(salesService.getByInvoiceNumber("INV-01-20260708-0042")).willReturn(detail);

        MvcTestResult result = mvc.get()
                .uri(URL + "/INV-01-20260708-0042")
                .header(STORE_ID_HEADER, "1")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson()
                .extractingPath("$.invoiceNumber").asString()
                .isEqualTo("INV-01-20260708-0042");
        assertThat(result).bodyJson()
                .extractingPath("$.items").asArray().hasSize(1);
    }

    @Test
    void getByInvoiceNumber_notFound_returns404() {
        given(salesService.getByInvoiceNumber("INV-NONEXISTENT"))
                .willThrow(new EntityNotFoundException("Sale not found: INV-NONEXISTENT"));

        MvcTestResult result = mvc.get()
                .uri(URL + "/INV-NONEXISTENT")
                .header(STORE_ID_HEADER, "1")
                .exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson()
                .extractingPath("$.code").asString().isEqualTo("ENTITY_NOT_FOUND");
    }

    @Test
    void getByInvoiceNumber_missingStoreIdHeader_returns400() {
        assertThat(mvc.get().uri(URL + "/INV-1"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("MISSING_STORE_ID");
    }
}
