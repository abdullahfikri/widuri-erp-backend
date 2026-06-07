package id.my.mfikriproject.widuri.erp.modules.inventory.service;

import id.my.mfikriproject.widuri.erp.modules.inventory.dto.CreateProductGroupRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.UpdateProductGroupRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductGroupService {
    Page<ProductGroupResponse> findAll(Pageable pageable);
    ProductGroupResponse findById(Long id);
    ProductGroupResponse create(CreateProductGroupRequest request);
    ProductGroupResponse update(Long id, UpdateProductGroupRequest request);
    void delete(Long id);
}
