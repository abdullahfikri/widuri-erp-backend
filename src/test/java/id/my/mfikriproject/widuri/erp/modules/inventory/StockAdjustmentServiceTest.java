package id.my.mfikriproject.widuri.erp.modules.inventory;

import id.my.mfikriproject.widuri.erp.core.context.StoreContext;
import id.my.mfikriproject.widuri.erp.core.exception.BusinessRuleException;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.StockAdjustRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductGroupModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.repository.ProductRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.impl.StockAdjustmentServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockAdjustmentServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private StockAdjustmentServiceImpl service;

    // --- adjustIn ---

    @Test
    void adjustIn_validQuantity_incrementsStockAndSaves() {
        StockAdjustRequest request = new StockAdjustRequest(10, "Pembelian dari supplier");
        ProductModel product = mockProduct(50);
        Query nativeQuery = mock(Query.class);
        given(productRepository.findByIdAndStoreModelIdForUpdate(1L, 1))
                .willReturn(Optional.of(product));
        given(entityManager.createNativeQuery(anyString())).willReturn(nativeQuery);
        given(nativeQuery.setParameter(anyString(), any())).willReturn(nativeQuery);
        given(nativeQuery.getSingleResult()).willReturn("app.stock_notes");
        given(productRepository.save(product)).willReturn(product);

        ProductResponse result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.adjustIn(1L, request));

        assertThat(result.stockQuantity()).isEqualTo(60);
        verify(productRepository).save(product);
    }

    @Test
    void adjustIn_productNotFound_throwsEntityNotFoundException() {
        StockAdjustRequest request = new StockAdjustRequest(10, "Restock");
        given(productRepository.findByIdAndStoreModelIdForUpdate(99L, 1))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.adjustIn(99L, request)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void adjustIn_setsSessionVariableBeforeSave() {
        StockAdjustRequest request = new StockAdjustRequest(5, "Retur dari customer");
        ProductModel product = mockProduct(10);
        Query nativeQuery = mock(Query.class);
        given(productRepository.findByIdAndStoreModelIdForUpdate(1L, 1))
                .willReturn(Optional.of(product));
        given(entityManager.createNativeQuery(anyString())).willReturn(nativeQuery);
        given(nativeQuery.setParameter(anyString(), any())).willReturn(nativeQuery);
        given(nativeQuery.getSingleResult()).willReturn("app.stock_notes");
        given(productRepository.save(product)).willReturn(product);

        ScopedValue.where(StoreContext.STORE_ID, 1)
                .run(() -> service.adjustIn(1L, request));

        verify(entityManager).createNativeQuery("SELECT set_config('app.stock_notes', :reason, true)");
        verify(nativeQuery).setParameter("reason", "Retur dari customer");
    }

    // --- adjustOut ---

    @Test
    void adjustOut_validQuantity_decrementsStock() {
        StockAdjustRequest request = new StockAdjustRequest(5, "Barang rusak");
        ProductModel product = mockProduct(30);
        Query nativeQuery = mock(Query.class);
        given(productRepository.findByIdAndStoreModelIdForUpdate(1L, 1))
                .willReturn(Optional.of(product));
        given(entityManager.createNativeQuery(anyString())).willReturn(nativeQuery);
        given(nativeQuery.setParameter(anyString(), any())).willReturn(nativeQuery);
        given(nativeQuery.getSingleResult()).willReturn("app.stock_notes");
        given(productRepository.save(product)).willReturn(product);

        ProductResponse result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.adjustOut(1L, request));

        assertThat(result.stockQuantity()).isEqualTo(25);
    }

    @Test
    void adjustOut_exactCurrentStock_decrementsToZero() {
        StockAdjustRequest request = new StockAdjustRequest(10, "Stok habis");
        ProductModel product = mockProduct(10);
        Query nativeQuery = mock(Query.class);
        given(productRepository.findByIdAndStoreModelIdForUpdate(1L, 1))
                .willReturn(Optional.of(product));
        given(entityManager.createNativeQuery(anyString())).willReturn(nativeQuery);
        given(nativeQuery.setParameter(anyString(), any())).willReturn(nativeQuery);
        given(nativeQuery.getSingleResult()).willReturn("app.stock_notes");
        given(productRepository.save(product)).willReturn(product);

        ProductResponse result = ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.adjustOut(1L, request));

        assertThat(result.stockQuantity()).isZero();
    }

    @Test
    void adjustOut_insufficientStock_throwsBusinessRuleException() {
        StockAdjustRequest request = new StockAdjustRequest(20, "Overcommit");
        ProductModel product = mock(ProductModel.class);
        doThrow(new BusinessRuleException("Insufficient stock for product 1 (requested: 20)"))
                .when(product).subtractStock(20);
        given(productRepository.findByIdAndStoreModelIdForUpdate(1L, 1))
                .willReturn(Optional.of(product));
        Query nativeQuery = mock(Query.class);
        given(entityManager.createNativeQuery(anyString())).willReturn(nativeQuery);
        given(nativeQuery.setParameter(anyString(), any())).willReturn(nativeQuery);
        given(nativeQuery.getSingleResult()).willReturn("app.stock_notes");

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.adjustOut(1L, request)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void adjustOut_productNotFound_throwsEntityNotFoundException() {
        StockAdjustRequest request = new StockAdjustRequest(5, "Rusak");
        given(productRepository.findByIdAndStoreModelIdForUpdate(99L, 1))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> ScopedValue.where(StoreContext.STORE_ID, 1)
                .call(() -> service.adjustOut(99L, request)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // --- null stockQuantity guards ---

    @Test
    void addStock_nullStockQuantity_doesNotThrowNPE() {
        ProductModel product = new ProductModel();
        product.addStock(10);
        assertThat(product.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void subtractStock_nullStockQuantity_treatsAsZero() {
        ProductModel product = new ProductModel();
        assertThatThrownBy(() -> product.subtractStock(5))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient stock for product null");
    }

    // --- helper ---

    private ProductModel mockProduct(int stockQuantity) {
        ProductModel product = mock(ProductModel.class);
        ProductGroupModel group = mock(ProductGroupModel.class);
        given(product.getId()).willReturn(1L);
        given(product.getProductGroupModel()).willReturn(group);
        given(group.getId()).willReturn(10L);
        given(product.getSku()).willReturn("SKU-001");
        given(product.getAttributes()).willReturn(Map.of());
        given(product.getLabelPrice()).willReturn(new BigDecimal("200.00"));
        given(product.getFloorPrice()).willReturn(new BigDecimal("150.00"));
        given(product.getStockQuantity()).willReturn(stockQuantity);
        given(product.getMinStockLevel()).willReturn(2);
        given(product.getCreatedAt()).willReturn(null);
        given(product.getUpdatedAt()).willReturn(null);

        // Delegate addStock/subtractStock to real logic on the mock's stockQuantity field
        // Since it's a mock, we use doAnswer to simulate real behavior
        doAnswer(inv -> {
            int delta = inv.getArgument(0);
            given(product.getStockQuantity()).willReturn(stockQuantity + delta);
            return null;
        }).when(product).addStock(anyInt());

        doAnswer(inv -> {
            int qty = inv.getArgument(0);
            if (stockQuantity < qty) {
                throw new BusinessRuleException(
                        "Insufficient stock for product " + product.getId() + " (requested: " + qty + ")");
            }
            given(product.getStockQuantity()).willReturn(stockQuantity - qty);
            return null;
        }).when(product).subtractStock(anyInt());

        return product;
    }
}
