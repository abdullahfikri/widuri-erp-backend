package id.my.mfikriproject.widuri.erp.modules.inventory.controller;

import id.my.mfikriproject.widuri.erp.core.config.WebMvcConfig;
import id.my.mfikriproject.widuri.erp.modules.inventory.ProductGroupService;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
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
}
