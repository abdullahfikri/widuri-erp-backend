package id.my.mfikriproject.widuri.erp.modules.inventory.service.impl;

import id.my.mfikriproject.widuri.erp.core.context.StoreContext;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.StockAdjustRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.repository.ProductRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.StockAdjustmentService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockAdjustmentServiceImpl implements StockAdjustmentService {
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    public StockAdjustmentServiceImpl(ProductRepository productRepository,
                                       EntityManager entityManager) {
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public ProductResponse adjustIn(Long productId, StockAdjustRequest request) {
        StoreContext.assertBound();
        Integer storeId = StoreContext.STORE_ID.get();

        ProductModel product = productRepository
                .findByIdAndStoreModelIdForUpdate(productId, storeId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        setStockNotes(request.reason());
        product.addStock(request.quantity());
        return ProductResponse.from(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse adjustOut(Long productId, StockAdjustRequest request) {
        StoreContext.assertBound();
        Integer storeId = StoreContext.STORE_ID.get();

        ProductModel product = productRepository
                .findByIdAndStoreModelIdForUpdate(productId, storeId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        setStockNotes(request.reason());
        product.subtractStock(request.quantity());
        return ProductResponse.from(productRepository.save(product));
    }

    private void setStockNotes(String reason) {
        entityManager.createNativeQuery(
                        "SELECT set_config('app.stock_notes', :reason, true)")
                .setParameter("reason", reason)
                .getSingleResult();
    }
}
