package id.my.mfikriproject.widuri.erp.modules.inventory.service.impl;

import id.my.mfikriproject.widuri.erp.core.exception.DuplicateEntityException;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductGroupModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.repository.ProductGroupRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.ProductGroupService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ProductGroupServiceImpl implements ProductGroupService {
    private final ProductGroupRepository productGroupRepository;

    public ProductGroupServiceImpl(ProductGroupRepository productGroupRepository) {
        this.productGroupRepository = productGroupRepository;
    }

    @Override
    public Page<ProductGroupResponse> findAll(Pageable pageable) {
        return productGroupRepository.findAll(pageable).map(ProductGroupResponse::from);
    }

    @Override
    public ProductGroupResponse findById(Long id) {
        return productGroupRepository.findById(id)
                .map(ProductGroupResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("ProductGroup not found"));
    }

    @Override
    public ProductGroupResponse create(ProductGroupRequest request) {
        String brand = normalizeBrand(request.brand());
        if (isDuplicate(request.name(), brand, null)) {
            throw new DuplicateEntityException("ProductGroup already exists");
        }

        ProductGroupModel productGroupModel = ProductGroupModel
                .builder()
                .name(request.name())
                .brand(brand)
                .category(request.category())
                .description(request.description())
                .build();

        return saveAndHandleDuplicate(productGroupModel);
    }

    @Override
    public ProductGroupResponse update(Long id, ProductGroupRequest request) {
        ProductGroupModel entity = productGroupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ProductGroup not found"));

        String brand = normalizeBrand(request.brand());
        if (isDuplicate(request.name(), brand, id)) {
            throw new DuplicateEntityException("ProductGroup already exists");
        }

        entity.updateFields(request.name(), brand, request.category(), request.description());
        return saveAndHandleDuplicate(entity);
    }

    @Override
    public void delete(Long id) {
        ProductGroupModel entity = productGroupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ProductGroup not found"));
        productGroupRepository.delete(entity);
    }

    private static final String UNIQUE_CONSTRAINT = "uq_product_group_name_brand";

    private ProductGroupResponse saveAndHandleDuplicate(ProductGroupModel entity) {
        try {
            return ProductGroupResponse.from(productGroupRepository.save(entity));
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException cve
                    && UNIQUE_CONSTRAINT.equals(cve.getConstraintName())) {
                throw new DuplicateEntityException("ProductGroup already exists");
            }
            throw e;
        }
    }

    private static String normalizeBrand(String brand) {
        return (brand == null || brand.isBlank()) ? null : brand.trim();
    }

    private boolean isDuplicate(String name, String brand, Long excludeId) {
        if (excludeId == null) {
            return brand != null
                    ? productGroupRepository.existsByNameAndBrand(name, brand)
                    : productGroupRepository.existsByNameAndBrandIsNull(name);
        }
        return brand != null
                ? productGroupRepository.existsByNameAndBrandAndIdNot(name, brand, excludeId)
                : productGroupRepository.existsByNameAndBrandIsNullAndIdNot(name, excludeId);
    }
}
