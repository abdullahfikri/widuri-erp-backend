package id.my.mfikriproject.widuri.erp.modules.inventory.controller;

import id.my.mfikriproject.widuri.erp.core.config.WebMvcConfig;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.StockAdjustRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.StockAdjustmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebMvcTest(StockAdjustmentController.class)
@Import(WebMvcConfig.class)
class StockAdjustmentControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private StockAdjustmentService stockAdjustmentService;

    private static final String BASE_URL = "/api/products";
    private static final String STORE_ID_HEADER = "X-Store-Id";

    private ProductResponse productResponse() {
        return new ProductResponse(1L, 10L, "SKU-001", Map.of(),
                new BigDecimal("100.00"), new BigDecimal("200.00"),
                new BigDecimal("150.00"), 60, 2, null, null);
    }

    // --- stock-in ---

    @Test
    void in_validRequest_returns200WithUpdatedStock() {
        given(stockAdjustmentService.adjustIn(eq(1L), any(StockAdjustRequest.class)))
                .willReturn(productResponse());

        MvcTestResult result = mvc.post().uri(BASE_URL + "/1/stock/in")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":10,\"reason\":\"Pembelian dari supplier\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.stockQuantity").asNumber().isEqualTo(60);
    }

    @Test
    void in_quantityZero_returns400() {
        MvcTestResult result = mvc.post().uri(BASE_URL + "/1/stock/in")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":0,\"reason\":\"Invalid\"}")
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void in_quantityNegative_returns400() {
        assertThat(mvc.post().uri(BASE_URL + "/1/stock/in")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":-1,\"reason\":\"Negative\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void in_blankReason_returns400() {
        assertThat(mvc.post().uri(BASE_URL + "/1/stock/in")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":10,\"reason\":\"\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void in_productNotFound_returns404() {
        given(stockAdjustmentService.adjustIn(eq(99L), any(StockAdjustRequest.class)))
                .willThrow(new EntityNotFoundException("Product not found"));

        assertThat(mvc.post().uri(BASE_URL + "/99/stock/in")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":10,\"reason\":\"Restock\"}"))
                .hasStatus(404)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("ENTITY_NOT_FOUND");
    }

    @Test
    void in_missingStoreIdHeader_returns400() {
        assertThat(mvc.post().uri(BASE_URL + "/1/stock/in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":10,\"reason\":\"Restock\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("MISSING_STORE_ID");
    }

    // --- stock-out ---

    @Test
    void out_validRequest_returns200WithUpdatedStock() {
        ProductResponse response = new ProductResponse(1L, 10L, "SKU-001", Map.of(),
                new BigDecimal("100.00"), new BigDecimal("200.00"),
                new BigDecimal("150.00"), 25, 2, null, null);
        given(stockAdjustmentService.adjustOut(eq(1L), any(StockAdjustRequest.class)))
                .willReturn(response);

        MvcTestResult result = mvc.post().uri(BASE_URL + "/1/stock/out")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":5,\"reason\":\"Barang rusak\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.stockQuantity").asNumber().isEqualTo(25);
    }

    @Test
    void out_insufficientStock_returns422() {
        given(stockAdjustmentService.adjustOut(eq(1L), any(StockAdjustRequest.class)))
                .willThrow(new IllegalArgumentException("Insufficient stock"));

        assertThat(mvc.post().uri(BASE_URL + "/1/stock/out")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":100,\"reason\":\"Overcommit\"}"))
                .hasStatus(422)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("INVALID_INPUT");
    }

    @Test
    void out_productNotFound_returns404() {
        given(stockAdjustmentService.adjustOut(eq(99L), any(StockAdjustRequest.class)))
                .willThrow(new EntityNotFoundException("Product not found"));

        assertThat(mvc.post().uri(BASE_URL + "/99/stock/out")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":5,\"reason\":\"Rusak\"}"))
                .hasStatus(404)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("ENTITY_NOT_FOUND");
    }

    @Test
    void out_missingStoreIdHeader_returns400() {
        assertThat(mvc.post().uri(BASE_URL + "/1/stock/out")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":5,\"reason\":\"Rusak\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("MISSING_STORE_ID");
    }
}
