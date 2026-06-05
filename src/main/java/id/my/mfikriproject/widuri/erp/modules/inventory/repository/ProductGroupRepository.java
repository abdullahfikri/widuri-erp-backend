package id.my.mfikriproject.widuri.erp.modules.inventory.repository;

import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductGroupModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductGroupRepository extends JpaRepository<ProductGroupModel, Long> {
    boolean existsByNameAndBrand(String name, String brand);
    boolean existsByNameAndBrandIsNull(String name);
}
