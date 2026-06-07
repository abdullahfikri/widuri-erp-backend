package id.my.mfikriproject.widuri.erp.modules.inventory.service.impl;

import id.my.mfikriproject.widuri.erp.core.context.StoreContext;
import id.my.mfikriproject.widuri.erp.core.entity.StoreModel;
import id.my.mfikriproject.widuri.erp.core.exception.DuplicateEntityException;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.CreateProductRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.UpdateProductRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductGroupModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.repository.ProductGroupRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.repository.ProductRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.ProductService;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.SkuGeneratorService;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

@Service
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final ProductGroupRepository productGroupRepository;
    private final SkuGeneratorService skuGeneratorService;
    private final EntityManager entityManager;

    public ProductServiceImpl(ProductRepository productRepository,
                              ProductGroupRepository productGroupRepository,
                              SkuGeneratorService skuGeneratorService,
                              EntityManager entityManager) {
        this.productRepository = productRepository;
        this.productGroupRepository = productGroupRepository;
        this.skuGeneratorService = skuGeneratorService;
        this.entityManager = entityManager;
    }

    @Override
    public Page<ProductResponse> findAll(Pageable pageable) {
        StoreContext.assertBound();
        Integer storeId = StoreContext.STORE_ID.get();
        return productRepository.findByStoreModelId(storeId, pageable)
                .map(ProductResponse::from);
    }

    @Override
    public ProductResponse findById(Long id) {
        StoreContext.assertBound();
        Integer storeId = StoreContext.STORE_ID.get();
        return productRepository.findByIdAndStoreModelId(id, storeId)
                .map(ProductResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
    }

    @Override
    public ProductResponse create(CreateProductRequest request) {
        StoreContext.assertBound();

        ProductGroupModel group = productGroupRepository.findById(request.productGroupId())
                .orElseThrow(() -> new EntityNotFoundException("ProductGroup not found"));

        validatePriceHierarchy(request.basePrice(), request.floorPrice(), request.labelPrice());

        String brand = group.getBrand() != null ? group.getBrand() : group.getName();
        String category = group.getCategory() != null ? group.getCategory() : group.getName();
        String sku = skuGeneratorService.generate(brand, category, request.skuAttribute());

        Integer storeId = StoreContext.STORE_ID.get();
        StoreModel storeRef = entityManager.getReference(StoreModel.class, storeId);

        Map<String, Object> attributes = request.attributes() != null
                ? request.attributes() : Collections.emptyMap();
        Integer stockQuantity = request.stockQuantity() != null ? request.stockQuantity() : 0;

        ProductModel product = ProductModel.builder()
                .productGroupModel(group)
                .storeModel(storeRef)
                .sku(sku)
                .attributes(attributes)
                .basePrice(request.basePrice())
                .labelPrice(request.labelPrice())
                .floorPrice(request.floorPrice())
                .stockQuantity(stockQuantity)
                .minStockLevel(0)
                .build();

        return saveAndHandleDuplicateSku(product);
    }

    @Override
    public ProductResponse update(Long id, UpdateProductRequest request) {
        StoreContext.assertBound();
        Integer storeId = StoreContext.STORE_ID.get();

        ProductModel entity = productRepository.findByIdAndStoreModelId(id, storeId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        validatePriceHierarchy(request.basePrice(), request.floorPrice(), request.labelPrice());

        entity.updateFields(request.attributes(), request.basePrice(), request.labelPrice(),
                request.floorPrice(), request.minStockLevel());

        return ProductResponse.from(productRepository.save(entity));
    }

    @Override
    public void delete(Long id) {
        StoreContext.assertBound();
        Integer storeId = StoreContext.STORE_ID.get();

        ProductModel entity = productRepository.findByIdAndStoreModelId(id, storeId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        productRepository.delete(entity);
    }

    private static final String SKU_CONSTRAINT = "uq_m_product_sku";

    private ProductResponse saveAndHandleDuplicateSku(ProductModel product) {
        try {
            return ProductResponse.from(productRepository.save(product));
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException cve
                    && SKU_CONSTRAINT.equals(cve.getConstraintName())) {
                throw new DuplicateEntityException("Product with this SKU already exists");
            }
            throw e;
        }
    }

    private static void validatePriceHierarchy(BigDecimal basePrice, BigDecimal floorPrice,
                                                BigDecimal labelPrice) {
        if (basePrice.compareTo(floorPrice) > 0) {
            throw new IllegalArgumentException("basePrice must be <= floorPrice");
        }
        if (floorPrice.compareTo(labelPrice) > 0) {
            throw new IllegalArgumentException("floorPrice must be <= labelPrice");
        }
    }
}
