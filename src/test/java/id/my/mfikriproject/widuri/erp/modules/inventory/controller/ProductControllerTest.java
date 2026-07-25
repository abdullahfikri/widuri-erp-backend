package id.my.mfikriproject.widuri.erp.modules.inventory.controller;

import id.my.mfikriproject.widuri.erp.core.config.WebMvcConfig;
import id.my.mfikriproject.widuri.erp.core.exception.BusinessRuleException;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.CreateProductRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.UpdateProductRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@WebMvcTest(ProductController.class)
@Import(WebMvcConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProductService productService;

    private static final String URL = "/api/products";
    private static final String STORE_ID_HEADER = "X-Store-Id";

    @BeforeEach
    void setUp() {
        given(productService.findAll(any(Pageable.class))).willReturn(Page.empty());
    }

    // --- findAll ---

    @Test
    void findAll_withValidHeader_returns200() {
        assertThat(mvc.get().uri(URL).header(STORE_ID_HEADER, "1"))
                .hasStatusOk();
    }

    @Test
    void findAll_returnsPagedContent() {
        ProductResponse response = new ProductResponse(1L, 10L, "SKU-001", Map.of(),
                new BigDecimal("200.00"), new BigDecimal("150.00"), 5, 2, null, null);
        given(productService.findAll(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(response)));

        MvcTestResult result = mvc.get().uri(URL).header(STORE_ID_HEADER, "1").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson()
                .extractingPath("$.content").asArray().hasSize(1);
        assertThat(result).bodyJson()
                .extractingPath("$.content[0].sku").asString().isEqualTo("SKU-001");
    }

    @Test
    void findAll_missingStoreIdHeader_returns400() {
        assertThat(mvc.get().uri(URL))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("MISSING_STORE_ID");
    }

    // --- findById ---

    @Test
    void findById_found_returns200WithBody() {
        ProductResponse response = new ProductResponse(1L, 10L, "SKU-001", Map.of("color", "Red"),
                new BigDecimal("200.00"), new BigDecimal("150.00"), 5, 2, null, null);
        given(productService.findById(1L)).willReturn(response);

        MvcTestResult result = mvc.get().uri(URL + "/1").header(STORE_ID_HEADER, "1").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.sku").asString().isEqualTo("SKU-001");
    }

    @Test
    void findById_notFound_returns404() {
        given(productService.findById(99L))
                .willThrow(new EntityNotFoundException("Product not found"));

        assertThat(mvc.get().uri(URL + "/99").header(STORE_ID_HEADER, "1"))
                .hasStatus(404)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("ENTITY_NOT_FOUND");
    }

    @Test
    void findById_missingStoreIdHeader_returns400() {
        assertThat(mvc.get().uri(URL + "/1"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("MISSING_STORE_ID");
    }

    @Test
    void findById_nonNumericId_returns400() {
        assertThat(mvc.get().uri(URL + "/abc").header(STORE_ID_HEADER, "1"))
                .hasStatus(400);
    }

    // --- create ---

    @Test
    void create_validRequest_returns201WithBody() {
        ProductResponse response = new ProductResponse(1L, 10L, "SHIMANO-REEL-SILVER-001",
                Map.of("color", "Red"), new BigDecimal("200.00"),
                new BigDecimal("150.00"), 5, null, null, null);
        given(productService.create(any(CreateProductRequest.class))).willReturn(response);

        MvcTestResult result = mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"productGroupId":10,"skuAttribute":"Silver","attributes":{"color":"Red"},"basePrice":100.00,"labelPrice":200.00,"floorPrice":150.00,"stockQuantity":5}""")
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.sku").asString().isEqualTo("SHIMANO-REEL-SILVER-001");
    }

    @Test
    void create_delegatesToServiceWithCorrectFields() {
        ProductResponse response = new ProductResponse(1L, 10L, "SKU-001", null,
                new BigDecimal("200.00"), new BigDecimal("150.00"), 5, null, null, null);
        given(productService.create(any(CreateProductRequest.class))).willReturn(response);

        mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productGroupId\":10,\"skuAttribute\":\"Silver\",\"basePrice\":100.00,\"labelPrice\":200.00,\"floorPrice\":150.00,\"stockQuantity\":5}")
                .exchange();

        ArgumentCaptor<CreateProductRequest> captor = ArgumentCaptor.forClass(CreateProductRequest.class);
        verify(productService).create(captor.capture());
        assertThat(captor.getValue().productGroupId()).isEqualTo(10L);
        assertThat(captor.getValue().skuAttribute()).isEqualTo("Silver");
        assertThat(captor.getValue().basePrice()).isEqualByComparingTo("100.00");
        assertThat(captor.getValue().stockQuantity()).isEqualTo(5);
    }

    @Test
    void create_groupNotFound_returns404() {
        given(productService.create(any(CreateProductRequest.class)))
                .willThrow(new EntityNotFoundException("ProductGroup not found"));

        assertThat(mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productGroupId\":99,\"skuAttribute\":\"Silver\",\"basePrice\":100.00,\"labelPrice\":200.00,\"floorPrice\":150.00}"))
                .hasStatus(404)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("ENTITY_NOT_FOUND");
    }

    @Test
    void create_invalidPrices_returns422() {
        given(productService.create(any(CreateProductRequest.class)))
                .willThrow(new BusinessRuleException("basePrice must be <= floorPrice"));

        assertThat(mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productGroupId\":10,\"skuAttribute\":\"Silver\",\"basePrice\":200.00,\"labelPrice\":300.00,\"floorPrice\":100.00}"))
                .hasStatus(422)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @Test
    void create_missingStoreIdHeader_returns400() {
        assertThat(mvc.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productGroupId\":10,\"skuAttribute\":\"Silver\",\"basePrice\":100.00,\"labelPrice\":200.00,\"floorPrice\":150.00}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("MISSING_STORE_ID");
    }

    @Test
    void create_blankSkuAttribute_returns400() {
        MvcTestResult result = mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productGroupId\":10,\"skuAttribute\":\"\",\"basePrice\":100.00,\"labelPrice\":200.00,\"floorPrice\":150.00}")
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void create_nullProductGroupId_returns400() {
        assertThat(mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skuAttribute\":\"Silver\",\"basePrice\":100.00,\"labelPrice\":200.00,\"floorPrice\":150.00}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void create_malformedJson_returns400() {
        assertThat(mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("INVALID_INPUT");
    }

    // --- update ---

    @Test
    void update_validRequest_returns200() {
        ProductResponse response = new ProductResponse(1L, 10L, "SKU-001", Map.of("color", "Blue"),
                new BigDecimal("250.00"), new BigDecimal("180.00"), 5, 3, null, null);
        given(productService.update(any(Long.class), any(UpdateProductRequest.class))).willReturn(response);

        MvcTestResult result = mvc.put().uri(URL + "/1")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attributes\":{\"color\":\"Blue\"},\"basePrice\":120.00,\"labelPrice\":250.00,\"floorPrice\":180.00,\"minStockLevel\":3}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.attributes.color").asString().isEqualTo("Blue");
    }

    @Test
    void update_notFound_returns404() {
        given(productService.update(any(Long.class), any(UpdateProductRequest.class)))
                .willThrow(new EntityNotFoundException("Product not found"));

        assertThat(mvc.put().uri(URL + "/99")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"basePrice\":100.00,\"labelPrice\":200.00,\"floorPrice\":150.00}"))
                .hasStatus(404)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("ENTITY_NOT_FOUND");
    }

    @Test
    void update_invalidPrices_returns422() {
        given(productService.update(any(Long.class), any(UpdateProductRequest.class)))
                .willThrow(new BusinessRuleException("floorPrice must be <= labelPrice"));

        assertThat(mvc.put().uri(URL + "/1")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"basePrice\":100.00,\"labelPrice\":200.00,\"floorPrice\":250.00}"))
                .hasStatus(422)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @Test
    void update_nullPrices_returns400() {
        assertThat(mvc.put().uri(URL + "/1")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attributes\":{}}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void update_missingStoreIdHeader_returns400() {
        assertThat(mvc.put().uri(URL + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"basePrice\":100.00,\"labelPrice\":200.00,\"floorPrice\":150.00}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("MISSING_STORE_ID");
    }

    @Test
    void update_nonNumericId_returns400() {
        assertThat(mvc.put().uri(URL + "/abc")
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"basePrice\":100.00,\"labelPrice\":200.00,\"floorPrice\":150.00}"))
                .hasStatus(400);
    }

    // --- delete ---

    @Test
    void delete_found_returns204() {
        assertThat(mvc.delete().uri(URL + "/1").header(STORE_ID_HEADER, "1"))
                .hasStatus(204);
    }

    @Test
    void delete_notFound_returns404() {
        doThrow(new EntityNotFoundException("Product not found"))
                .when(productService).delete(99L);

        assertThat(mvc.delete().uri(URL + "/99").header(STORE_ID_HEADER, "1"))
                .hasStatus(404)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("ENTITY_NOT_FOUND");
    }

    @Test
    void delete_missingStoreIdHeader_returns400() {
        assertThat(mvc.delete().uri(URL + "/1"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("MISSING_STORE_ID");
    }

    @Test
    void delete_nonNumericId_returns400() {
        assertThat(mvc.delete().uri(URL + "/abc").header(STORE_ID_HEADER, "1"))
                .hasStatus(400);
    }
}
