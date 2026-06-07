package id.my.mfikriproject.widuri.erp.modules.inventory.repository;

import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SkuRepository extends JpaRepository<ProductModel, Long> {
    @Query(value = "SELECT get_next_sku_seq()", nativeQuery = true)
    String getNextSkuSequence();
}
