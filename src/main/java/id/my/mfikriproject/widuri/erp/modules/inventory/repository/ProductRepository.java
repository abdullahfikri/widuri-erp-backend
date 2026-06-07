package id.my.mfikriproject.widuri.erp.modules.inventory.repository;

import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductModel, Long> {
    Page<ProductModel> findByStoreModelId(Integer storeId, Pageable pageable);
    Optional<ProductModel> findByIdAndStoreModelId(Long id, Integer storeId);
}
