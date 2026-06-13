package id.my.mfikriproject.widuri.erp.modules.inventory.service;

import id.my.mfikriproject.widuri.erp.modules.inventory.dto.CreateProductRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.UpdateProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    Page<ProductResponse> findAll(Pageable pageable);
    ProductResponse findById(Long id);
    ProductResponse create(CreateProductRequest request);
    ProductResponse update(Long id, UpdateProductRequest request);
    void delete(Long id);
}
