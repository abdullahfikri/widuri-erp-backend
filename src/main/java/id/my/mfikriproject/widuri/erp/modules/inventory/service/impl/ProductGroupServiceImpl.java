package id.my.mfikriproject.widuri.erp.modules.inventory.service.impl;

import id.my.mfikriproject.widuri.erp.core.exception.DuplicateEntityException;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.CreateProductGroupRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.UpdateProductGroupRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductGroupModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.repository.ProductGroupRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.ProductGroupService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ProductGroupServiceImpl implements ProductGroupService {
    private final ProductGroupRepository productGroupRepository;

    public ProductGroupServiceImpl(ProductGroupRepository productGroupRepository) {
        this.productGroupRepository = productGroupRepository;
    }

    public Page<ProductGroupResponse> findAll(Pageable pageable) {
        return productGroupRepository.findAll(pageable).map(ProductGroupResponse::from);
    }

    public ProductGroupResponse findById(Long id) {
        return productGroupRepository.findById(id)
                .map(ProductGroupResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("ProductGroup not found"));
    }

    @Override
    public ProductGroupResponse create(CreateProductGroupRequest request) {
        boolean duplicate = request.brand() != null
                ? productGroupRepository.existsByNameAndBrand(request.name(), request.brand())
                : productGroupRepository.existsByNameAndBrandIsNull(request.name());

        if (duplicate) {
            throw new DuplicateEntityException("ProductGroup already exists");
        }

        ProductGroupModel productGroupModel = ProductGroupModel
                .builder()
                .name(request.name())
                .brand(request.brand())
                .category(request.category())
                .description(request.description())
                .build();

        return ProductGroupResponse.from(productGroupRepository.save(productGroupModel));
    }

    @Override
    public ProductGroupResponse update(Long id, UpdateProductGroupRequest request) {
        ProductGroupModel entity = productGroupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ProductGroup not found"));

        boolean duplicate = request.brand() != null
                ? productGroupRepository.existsByNameAndBrandAndIdNot(request.name(), request.brand(), id)
                : productGroupRepository.existsByNameAndBrandIsNullAndIdNot(request.name(), id);

        if (duplicate) {
            throw new DuplicateEntityException("ProductGroup already exists");
        }

        entity.updateFields(request.name(), request.brand(), request.category(), request.description());
        return ProductGroupResponse.from(productGroupRepository.save(entity));
    }

    @Override
    public void delete(Long id) {
        if (!productGroupRepository.existsById(id)) {
            throw new EntityNotFoundException("ProductGroup not found");
        }
        productGroupRepository.deleteById(id);
    }
}
