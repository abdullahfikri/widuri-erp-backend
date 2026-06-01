package id.my.mfikriproject.widuri.erp.modules.inventory.service;

import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductGroupService {
    Page<ProductGroupResponse> findAll(Pageable pageable);
    ProductGroupResponse findById(Long id);
}
