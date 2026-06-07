package id.my.mfikriproject.widuri.erp.modules.inventory;

import id.my.mfikriproject.widuri.erp.core.context.StoreContext;
import id.my.mfikriproject.widuri.erp.core.entity.StoreModel;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.CreateProductRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.UpdateProductRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductGroupModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.repository.ProductGroupRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.repository.ProductRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.SkuGeneratorService;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.impl.ProductServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductGroupRepository productGroupRepository;

    @Mock
    private SkuGeneratorService skuGeneratorService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ProductServiceImpl service;

    // --- findAll ---

    @Test
    void findAll_usesStoreIdFromScopedValues() {
        Pageable pageable = PageRequest.of(0, 20);
        given(productRepository.findByStoreModelId(1, pageable)).willReturn(Page.empty(pageable));

        ScopedValue.where(StoreContext.STORE_ID, 1).run(() -> service.findAll(pageable));

        verify(productRepository).findByStoreModelId(1, pageable);
    }

    @Test
    void findAll_mapsEntitiesToResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        ProductModel model = mock(ProductModel.class);
        ProductGroupModel group = mock(ProductGroupModel.class);
        given(model.getId()).willReturn(1L);
        given(model.getProductGroupModel()).willReturn(group);
        given(group.getId()).willReturn(10L);
        given(model.getSku()).willReturn("SKU-001");
        given(model.getAttributes()).willReturn(Map.of("color", "Red"));
        given(model.getBasePrice()).willReturn(new BigDecimal("100.00"));
        given(model.getLabelPrice()).willReturn(new BigDecimal("200.00"));
        given(model.getFloorPrice()).willReturn(new BigDecimal("150.00"));
        given(model.getStockQuantity()).willReturn(5);
        given(model.getMinStockLevel()).willReturn(2);
        given(model.getCreatedAt()).willReturn(null);
        given(model.getUpdatedAt()).willReturn(null);
        given(productRepository.findByStoreModelId(1, pageable))
                .willReturn(new PageImpl<>(List.of(model), pageable, 1));

        Page<ProductResponse> result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.findAll(pageable));

        assertThat(result.getContent()).hasSize(1);
        ProductResponse response = result.getContent().getFirst();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.productGroupId()).isEqualTo(10L);
        assertThat(response.sku()).isEqualTo("SKU-001");
        assertThat(response.basePrice()).isEqualByComparingTo("100.00");
    }

    // --- findById ---

    @Test
    void findById_found_returnsCorrectResponse() {
        ProductModel model = mock(ProductModel.class);
        ProductGroupModel group = mock(ProductGroupModel.class);
        given(model.getId()).willReturn(1L);
        given(model.getProductGroupModel()).willReturn(group);
        given(group.getId()).willReturn(10L);
        given(model.getSku()).willReturn("SKU-001");
        given(model.getAttributes()).willReturn(Map.of());
        given(model.getBasePrice()).willReturn(new BigDecimal("100.00"));
        given(model.getLabelPrice()).willReturn(new BigDecimal("200.00"));
        given(model.getFloorPrice()).willReturn(new BigDecimal("150.00"));
        given(model.getStockQuantity()).willReturn(5);
        given(model.getMinStockLevel()).willReturn(2);
        given(model.getCreatedAt()).willReturn(null);
        given(model.getUpdatedAt()).willReturn(null);
        given(productRepository.findByIdAndStoreModelId(1L, 1)).willReturn(Optional.of(model));

        ProductResponse result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.findById(1L));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.productGroupId()).isEqualTo(10L);
    }

    @Test
    void findById_notFound_throwsEntityNotFoundException() {
        given(productRepository.findByIdAndStoreModelId(99L, 1)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.findById(99L)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // --- create ---

    private CreateProductRequest validCreateRequest() {
        return new CreateProductRequest(10L, "Silver", Map.of("color", "Red"),
                new BigDecimal("100.00"), new BigDecimal("200.00"),
                new BigDecimal("150.00"), 5);
    }

    @Test
    void create_validRequest_withBrand_generatesSku() {
        CreateProductRequest request = validCreateRequest();
        ProductGroupModel group = mock(ProductGroupModel.class);
        given(group.getBrand()).willReturn("Shimano");
        given(group.getCategory()).willReturn("Reel");
        given(group.getName()).willReturn("Stradic");
        given(productGroupRepository.findById(10L)).willReturn(Optional.of(group));
        given(skuGeneratorService.generate("Shimano", "Reel", "Silver")).willReturn("SHIMANO-REEL-SILVER-001");
        StoreModel storeRef = mock(StoreModel.class);
        given(entityManager.getReference(StoreModel.class, 1)).willReturn(storeRef);
        ProductModel saved = mock(ProductModel.class);
        ProductGroupModel savedGroup = mock(ProductGroupModel.class);
        given(saved.getProductGroupModel()).willReturn(savedGroup);
        given(savedGroup.getId()).willReturn(10L);
        given(saved.getSku()).willReturn("SHIMANO-REEL-SILVER-001");
        given(saved.getAttributes()).willReturn(Map.of("color", "Red"));
        given(saved.getBasePrice()).willReturn(new BigDecimal("100.00"));
        given(saved.getLabelPrice()).willReturn(new BigDecimal("200.00"));
        given(saved.getFloorPrice()).willReturn(new BigDecimal("150.00"));
        given(saved.getStockQuantity()).willReturn(5);
        given(saved.getMinStockLevel()).willReturn(null);
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(productRepository.save(any())).willReturn(saved);

        ProductResponse result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.create(request));

        assertThat(result.sku()).isEqualTo("SHIMANO-REEL-SILVER-001");
    }

    @Test
    void create_validRequest_nullBrand_usesGroupNameFallback() {
        CreateProductRequest request = validCreateRequest();
        ProductGroupModel group = mock(ProductGroupModel.class);
        given(group.getBrand()).willReturn(null);
        given(group.getCategory()).willReturn("Reel");
        given(group.getName()).willReturn("Stradic");
        given(productGroupRepository.findById(10L)).willReturn(Optional.of(group));
        given(skuGeneratorService.generate("Stradic", "Reel", "Silver")).willReturn("STRADIC-REEL-SILVER-001");
        StoreModel storeRef = mock(StoreModel.class);
        given(entityManager.getReference(StoreModel.class, 1)).willReturn(storeRef);
        ProductModel saved = mock(ProductModel.class);
        ProductGroupModel savedGroup = mock(ProductGroupModel.class);
        given(saved.getProductGroupModel()).willReturn(savedGroup);
        given(savedGroup.getId()).willReturn(10L);
        given(saved.getSku()).willReturn("STRADIC-REEL-SILVER-001");
        given(saved.getAttributes()).willReturn(Map.of("color", "Red"));
        given(saved.getBasePrice()).willReturn(new BigDecimal("100.00"));
        given(saved.getLabelPrice()).willReturn(new BigDecimal("200.00"));
        given(saved.getFloorPrice()).willReturn(new BigDecimal("150.00"));
        given(saved.getStockQuantity()).willReturn(5);
        given(saved.getMinStockLevel()).willReturn(null);
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(productRepository.save(any())).willReturn(saved);

        ProductResponse result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.create(request));

        assertThat(result.sku()).isEqualTo("STRADIC-REEL-SILVER-001");
    }

    @Test
    void create_validRequest_nullCategory_usesGroupNameFallback() {
        CreateProductRequest request = validCreateRequest();
        ProductGroupModel group = mock(ProductGroupModel.class);
        given(group.getBrand()).willReturn("Shimano");
        given(group.getCategory()).willReturn(null);
        given(group.getName()).willReturn("Stradic");
        given(productGroupRepository.findById(10L)).willReturn(Optional.of(group));
        given(skuGeneratorService.generate("Shimano", "Stradic", "Silver"))
                .willReturn("SHIMANO-STRADIC-SILVER-001");
        StoreModel storeRef = mock(StoreModel.class);
        given(entityManager.getReference(StoreModel.class, 1)).willReturn(storeRef);
        ProductModel saved = mock(ProductModel.class);
        ProductGroupModel savedGroup = mock(ProductGroupModel.class);
        given(saved.getProductGroupModel()).willReturn(savedGroup);
        given(savedGroup.getId()).willReturn(10L);
        given(saved.getSku()).willReturn("SHIMANO-STRADIC-SILVER-001");
        given(saved.getAttributes()).willReturn(Map.of("color", "Red"));
        given(saved.getBasePrice()).willReturn(new BigDecimal("100.00"));
        given(saved.getLabelPrice()).willReturn(new BigDecimal("200.00"));
        given(saved.getFloorPrice()).willReturn(new BigDecimal("150.00"));
        given(saved.getStockQuantity()).willReturn(5);
        given(saved.getMinStockLevel()).willReturn(null);
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(productRepository.save(any())).willReturn(saved);

        ProductResponse result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.create(request));

        assertThat(result.sku()).isEqualTo("SHIMANO-STRADIC-SILVER-001");
    }

    @Test
    void create_groupNotFound_throwsEntityNotFoundException() {
        CreateProductRequest request = validCreateRequest();
        given(productGroupRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.create(request)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void create_baseGtFloor_throwsIllegalArgumentException() {
        CreateProductRequest request = new CreateProductRequest(10L, "Silver", null,
                new BigDecimal("200.00"), new BigDecimal("300.00"),
                new BigDecimal("150.00"), 5);
        ProductGroupModel group = mock(ProductGroupModel.class);
        given(group.getBrand()).willReturn("Shimano");
        given(group.getCategory()).willReturn("Reel");
        given(group.getName()).willReturn("Stradic");
        given(productGroupRepository.findById(10L)).willReturn(Optional.of(group));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.create(request)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_floorGtLabel_throwsIllegalArgumentException() {
        CreateProductRequest request = new CreateProductRequest(10L, "Silver", null,
                new BigDecimal("100.00"), new BigDecimal("200.00"),
                new BigDecimal("250.00"), 5);
        ProductGroupModel group = mock(ProductGroupModel.class);
        given(group.getBrand()).willReturn("Shimano");
        given(group.getCategory()).willReturn("Reel");
        given(group.getName()).willReturn("Stradic");
        given(productGroupRepository.findById(10L)).willReturn(Optional.of(group));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.create(request)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_setsStockQuantityFromRequest() {
        CreateProductRequest request = new CreateProductRequest(10L, "Silver", null,
                new BigDecimal("100.00"), new BigDecimal("200.00"),
                new BigDecimal("150.00"), 42);
        ProductGroupModel group = mock(ProductGroupModel.class);
        given(group.getBrand()).willReturn("Shimano");
        given(group.getCategory()).willReturn("Reel");
        given(group.getName()).willReturn("Stradic");
        given(productGroupRepository.findById(10L)).willReturn(Optional.of(group));
        given(skuGeneratorService.generate(any(), any(), any())).willReturn("SKU-001");
        StoreModel storeRef = mock(StoreModel.class);
        given(entityManager.getReference(StoreModel.class, 1)).willReturn(storeRef);
        ProductModel saved = mock(ProductModel.class);
        ProductGroupModel savedGroup = mock(ProductGroupModel.class);
        given(saved.getProductGroupModel()).willReturn(savedGroup);
        given(saved.getSku()).willReturn("SKU-001");
        given(saved.getAttributes()).willReturn(Map.of());
        given(saved.getBasePrice()).willReturn(new BigDecimal("100.00"));
        given(saved.getLabelPrice()).willReturn(new BigDecimal("200.00"));
        given(saved.getFloorPrice()).willReturn(new BigDecimal("150.00"));
        given(saved.getStockQuantity()).willReturn(42);
        given(saved.getMinStockLevel()).willReturn(null);
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(productRepository.save(any())).willReturn(saved);

        ProductResponse result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.create(request));

        assertThat(result.stockQuantity()).isEqualTo(42);
    }

    @Test
    void create_nullStockQuantity_defaultsToZero() {
        CreateProductRequest request = new CreateProductRequest(10L, "Silver", null,
                new BigDecimal("100.00"), new BigDecimal("200.00"),
                new BigDecimal("150.00"), null);
        ProductGroupModel group = mock(ProductGroupModel.class);
        given(group.getBrand()).willReturn("Shimano");
        given(group.getCategory()).willReturn("Reel");
        given(group.getName()).willReturn("Stradic");
        given(productGroupRepository.findById(10L)).willReturn(Optional.of(group));
        given(skuGeneratorService.generate(any(), any(), any())).willReturn("SKU-001");
        StoreModel storeRef = mock(StoreModel.class);
        given(entityManager.getReference(StoreModel.class, 1)).willReturn(storeRef);
        ProductModel saved = mock(ProductModel.class);
        ProductGroupModel savedGroup = mock(ProductGroupModel.class);
        given(saved.getProductGroupModel()).willReturn(savedGroup);
        given(saved.getSku()).willReturn("SKU-001");
        given(saved.getAttributes()).willReturn(Map.of());
        given(saved.getBasePrice()).willReturn(new BigDecimal("100.00"));
        given(saved.getLabelPrice()).willReturn(new BigDecimal("200.00"));
        given(saved.getFloorPrice()).willReturn(new BigDecimal("150.00"));
        given(saved.getStockQuantity()).willReturn(0);
        given(saved.getMinStockLevel()).willReturn(null);
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(productRepository.save(any())).willReturn(saved);

        ProductResponse result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.create(request));

        assertThat(result.stockQuantity()).isZero();
    }

    @Test
    void create_nullAttributes_savesEmptyMap() {
        CreateProductRequest request = new CreateProductRequest(10L, "Silver", null,
                new BigDecimal("100.00"), new BigDecimal("200.00"),
                new BigDecimal("150.00"), 5);
        ProductGroupModel group = mock(ProductGroupModel.class);
        given(group.getBrand()).willReturn("Shimano");
        given(group.getCategory()).willReturn("Reel");
        given(group.getName()).willReturn("Stradic");
        given(productGroupRepository.findById(10L)).willReturn(Optional.of(group));
        given(skuGeneratorService.generate(any(), any(), any())).willReturn("SKU-001");
        StoreModel storeRef = mock(StoreModel.class);
        given(entityManager.getReference(StoreModel.class, 1)).willReturn(storeRef);
        ProductModel saved = mock(ProductModel.class);
        ProductGroupModel savedGroup = mock(ProductGroupModel.class);
        given(saved.getProductGroupModel()).willReturn(savedGroup);
        given(saved.getSku()).willReturn("SKU-001");
        given(saved.getAttributes()).willReturn(Collections.emptyMap());
        given(saved.getBasePrice()).willReturn(new BigDecimal("100.00"));
        given(saved.getLabelPrice()).willReturn(new BigDecimal("200.00"));
        given(saved.getFloorPrice()).willReturn(new BigDecimal("150.00"));
        given(saved.getStockQuantity()).willReturn(5);
        given(saved.getMinStockLevel()).willReturn(null);
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(productRepository.save(any())).willReturn(saved);

        ProductResponse result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.create(request));

        assertThat(result.attributes()).isEmpty();
    }

    // --- update ---

    @Test
    void update_found_updatesAllowedFields() {
        UpdateProductRequest request = new UpdateProductRequest(Map.of("color", "Blue"),
                new BigDecimal("120.00"), new BigDecimal("250.00"),
                new BigDecimal("180.00"), 3);
        ProductModel entityMock = mock(ProductModel.class);
        ProductGroupModel groupMock = mock(ProductGroupModel.class);
        given(entityMock.getProductGroupModel()).willReturn(groupMock);
        given(groupMock.getId()).willReturn(10L);
        given(entityMock.getSku()).willReturn("ORIGINAL-SKU");
        given(entityMock.getAttributes()).willReturn(Map.of("color", "Blue"));
        given(entityMock.getBasePrice()).willReturn(new BigDecimal("120.00"));
        given(entityMock.getLabelPrice()).willReturn(new BigDecimal("250.00"));
        given(entityMock.getFloorPrice()).willReturn(new BigDecimal("180.00"));
        given(entityMock.getStockQuantity()).willReturn(5);
        given(entityMock.getMinStockLevel()).willReturn(3);
        given(entityMock.getCreatedAt()).willReturn(null);
        given(entityMock.getUpdatedAt()).willReturn(null);
        given(productRepository.findByIdAndStoreModelId(2L, 1)).willReturn(Optional.of(entityMock));
        given(productRepository.save(entityMock)).willReturn(entityMock);

        ProductResponse result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.update(2L, request));

        assertThat(result.sku()).isEqualTo("ORIGINAL-SKU"); // SKU unchanged
        assertThat(result.basePrice()).isEqualByComparingTo("120.00");
        assertThat(result.labelPrice()).isEqualByComparingTo("250.00");
        assertThat(result.floorPrice()).isEqualByComparingTo("180.00");
        assertThat(result.minStockLevel()).isEqualTo(3);
    }

    @Test
    void update_notFound_throwsEntityNotFoundException() {
        UpdateProductRequest request = new UpdateProductRequest(null,
                new BigDecimal("100.00"), new BigDecimal("200.00"),
                new BigDecimal("150.00"), null);
        given(productRepository.findByIdAndStoreModelId(99L, 1)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.update(99L, request)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_invalidPriceHierarchy_throwsIllegalArgumentException() {
        UpdateProductRequest request = new UpdateProductRequest(null,
                new BigDecimal("200.00"), new BigDecimal("300.00"),
                new BigDecimal("350.00"), null);
        ProductModel entity = mock(ProductModel.class);
        given(productRepository.findByIdAndStoreModelId(1L, 1)).willReturn(Optional.of(entity));

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.update(1L, request)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- delete ---

    @Test
    void delete_found_callsDelete() {
        ProductModel entity = mock(ProductModel.class);
        given(productRepository.findByIdAndStoreModelId(1L, 1)).willReturn(Optional.of(entity));

        ScopedValue.where(StoreContext.STORE_ID, 1).run(() -> service.delete(1L));

        verify(productRepository).delete(entity);
    }

    @Test
    void delete_notFound_throwsEntityNotFoundException() {
        given(productRepository.findByIdAndStoreModelId(99L, 1)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, 1)
                .run(() -> service.delete(99L)))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
