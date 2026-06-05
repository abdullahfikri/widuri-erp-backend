package id.my.mfikriproject.widuri.erp.modules.inventory.controller;

import id.my.mfikriproject.widuri.erp.core.config.WebMvcConfig;
import id.my.mfikriproject.widuri.erp.core.exception.DuplicateEntityException;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.CreateProductGroupRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.ProductGroupService;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebMvcTest(ProductGroupController.class)
@Import(WebMvcConfig.class)
class ProductGroupControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProductGroupService productGroupService;

    private static final String URL = "/api/product-groups";
    private static final String STORE_ID_HEADER = "X-Store-Id";

    @BeforeEach
    void setUp() {
        given(productGroupService.findAll(any(Pageable.class))).willReturn(Page.empty());
    }

    @Test
    void findAll_withValidHeader_returns200() {
        assertThat(mvc.get().uri(URL).header(STORE_ID_HEADER, "1"))
                .hasStatusOk();
    }

    @Test
    void findAll_defaultPagination_usesPageableDefaults() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        assertThat(mvc.get().uri(URL).header(STORE_ID_HEADER, "1"))
                .hasStatusOk();

        verify(productGroupService).findAll(captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("name")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void findAll_customPagination_forwardsToService() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        assertThat(mvc.get().uri(URL + "?page=2&size=5&sort=brand,desc").header(STORE_ID_HEADER, "1"))
                .hasStatusOk();

        verify(productGroupService).findAll(captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("brand")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("brand").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void findAll_pageSizeExceedsMax_isCappedAt100() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        assertThat(mvc.get().uri(URL + "?size=999").header(STORE_ID_HEADER, "1"))
                .hasStatusOk();

        verify(productGroupService).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void findAll_missingStoreIdHeader_returns400() {
        assertThat(mvc.get().uri(URL))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .asString().isEqualTo("MISSING_STORE_ID");
    }

    @Test
    void findAll_invalidStoreIdHeader_returns400() {
        assertThat(mvc.get().uri(URL).header(STORE_ID_HEADER, "abc"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .asString().isEqualTo("INVALID_STORE_ID");
    }

    @Test
    void findAll_nonPositiveStoreId_returns400() {
        assertThat(mvc.get().uri(URL).header(STORE_ID_HEADER, "0"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .asString().isEqualTo("INVALID_STORE_ID");
    }

    @Test
    void findById_found_returns200WithBody() {
        ProductGroupResponse response = new ProductGroupResponse(1L, "Joran Test", "Shimano", "Rod", null, null, null);
        given(productGroupService.findById(1L)).willReturn(response);

        MvcTestResult result = mvc.get().uri(URL + "/1").header(STORE_ID_HEADER, "1").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.name").asString().isEqualTo("Joran Test");
        assertThat(result).bodyJson().extractingPath("$.brand").asString().isEqualTo("Shimano");
    }

    @Test
    void findById_notFound_returns404() {
        given(productGroupService.findById(99L))
                .willThrow(new EntityNotFoundException("ProductGroup not found"));

        assertThat(mvc.get().uri(URL + "/99").header(STORE_ID_HEADER, "1"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code")
                .asString().isEqualTo("ENTITY_NOT_FOUND");
    }

    @Test
    void findById_missingStoreIdHeader_returns400() {
        assertThat(mvc.get().uri(URL + "/1"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .asString().isEqualTo("MISSING_STORE_ID");
    }

    @Test
    void findAll_returnsPagedContent() {
        ProductGroupResponse response = new ProductGroupResponse(1L, "Joran Test", "Shimano", "Rod", null, null, null);
        given(productGroupService.findAll(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(response)));

        MvcTestResult result = mvc.get().uri(URL).header(STORE_ID_HEADER, "1").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson()
                .extractingPath("$.content")
                .asArray().hasSize(1);
        assertThat(result).bodyJson()
                .extractingPath("$.content[0].id")
                .asNumber().isEqualTo(1);
        assertThat(result).bodyJson()
                .extractingPath("$.content[0].name")
                .asString().isEqualTo("Joran Test");
        assertThat(result).bodyJson()
                .extractingPath("$.content[0].brand")
                .asString().isEqualTo("Shimano");
    }

    @Test
    void create_validRequest_returns201WithBody() {
        ProductGroupResponse response = new ProductGroupResponse(1L, "Reel Spinning", "Shimano", "Reel", null, null, null);
        given(productGroupService.create(any(CreateProductGroupRequest.class))).willReturn(response);

        MvcTestResult result = mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Reel Spinning\",\"brand\":\"Shimano\",\"category\":\"Reel\"}")
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.name").asString().isEqualTo("Reel Spinning");
        assertThat(result).bodyJson().extractingPath("$.brand").asString().isEqualTo("Shimano");
    }

    @Test
    void create_nullBrand_returns201() {
        ProductGroupResponse response = new ProductGroupResponse(2L, "Reel Spinning", null, null, null, null, null);
        given(productGroupService.create(any(CreateProductGroupRequest.class))).willReturn(response);

        assertThat(mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Reel Spinning\"}"))
                .hasStatus(201);
    }

    @Test
    void create_delegatesToServiceWithCorrectFields() {
        ProductGroupResponse response = new ProductGroupResponse(1L, "Reel Spinning", "Shimano", "Reel", null, null, null);
        given(productGroupService.create(any(CreateProductGroupRequest.class))).willReturn(response);

        mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Reel Spinning\",\"brand\":\"Shimano\",\"category\":\"Reel\"}")
                .exchange();

        ArgumentCaptor<CreateProductGroupRequest> captor = ArgumentCaptor.forClass(CreateProductGroupRequest.class);
        verify(productGroupService).create(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Reel Spinning");
        assertThat(captor.getValue().brand()).isEqualTo("Shimano");
        assertThat(captor.getValue().category()).isEqualTo("Reel");
    }

    @Test
    void create_blankName_returns400WithValidationDetails() {
        MvcTestResult result = mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"brand\":\"Shimano\"}")
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.details").asArray().isNotEmpty();
    }

    @Test
    void create_nullName_returns400() {
        assertThat(mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"brand\":\"Shimano\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void create_brandTooLong_returns400() {
        String longBrand = "S".repeat(101);
        assertThat(mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Reel Spinning\",\"brand\":\"" + longBrand + "\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void create_duplicateEntity_returns409WithCode() {
        given(productGroupService.create(any(CreateProductGroupRequest.class)))
                .willThrow(new DuplicateEntityException("ProductGroup already exists"));

        assertThat(mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Reel Spinning\",\"brand\":\"Shimano\"}"))
                .hasStatus(409)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("DUPLICATE_ENTITY");
    }

    @Test
    void create_missingStoreIdHeader_returns400() {
        assertThat(mvc.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Reel Spinning\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").asString().isEqualTo("MISSING_STORE_ID");
    }

    // XSS: API menerima payload sebagai string biasa; perlindungan utama ada di Content-Type: application/json
    // yang mencegah browser mengeksekusi payload sebagai HTML.

    @Test
    void create_xssPayloadInName_isAcceptedAndResponseIsJson() {
        // Script tag dalam name — valid sebagai string, harus lolos masuk ke service
        String xssName = "<script>alert('xss')</script>";
        ProductGroupResponse response = new ProductGroupResponse(1L, xssName, null, null, null, null, null);
        given(productGroupService.create(any(CreateProductGroupRequest.class))).willReturn(response);

        MvcTestResult result = mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"<script>alert('xss')</script>\"}")
                .exchange();

        // Payload diterima — bukan input yang invalid
        assertThat(result).hasStatus(201);
        // Content-Type harus application/json, bukan text/html — ini yang mencegah eksekusi di browser
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        // Payload dikembalikan sebagai JSON string, bukan dieksekusi
        assertThat(result).bodyJson().extractingPath("$.name").asString().isEqualTo(xssName);
    }

    @Test
    void create_xssPayloadInDescription_isAcceptedAndResponseIsJson() {
        // Img onerror — vektor XSS umum selain script tag
        String xssDescription = "<img src=x onerror=alert('xss')>";
        ProductGroupResponse response = new ProductGroupResponse(1L, "Reel", null, null, xssDescription, null, null);
        given(productGroupService.create(any(CreateProductGroupRequest.class))).willReturn(response);

        MvcTestResult result = mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Reel\",\"description\":\"<img src=x onerror=alert('xss')>\"}")
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        assertThat(result).bodyJson().extractingPath("$.description").asString().isEqualTo(xssDescription);
    }

    @Test
    void create_xssPayload_serviceReceivesRawUnmodifiedString() {
        // Verifikasi: server tidak mengubah (strip/encode) payload sebelum diteruskan ke service
        // Sanitasi adalah tanggung jawab layer frontend saat merender ke HTML
        given(productGroupService.create(any(CreateProductGroupRequest.class)))
                .willReturn(new ProductGroupResponse(1L, "x", null, null, null, null, null));

        mvc.post().uri(URL)
                .header(STORE_ID_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"<script>alert(1)</script>\"}")
                .exchange();

        ArgumentCaptor<CreateProductGroupRequest> captor = ArgumentCaptor.forClass(CreateProductGroupRequest.class);
        verify(productGroupService).create(captor.capture());
        // String harus sampai ke service persis seperti yang dikirim — tidak di-strip server
        assertThat(captor.getValue().name()).isEqualTo("<script>alert(1)</script>");
    }
}
