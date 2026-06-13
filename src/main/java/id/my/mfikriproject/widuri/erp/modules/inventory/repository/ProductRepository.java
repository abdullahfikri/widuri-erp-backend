package id.my.mfikriproject.widuri.erp.modules.inventory.repository;

import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductModel, Long> {
    Page<ProductModel> findByStoreModelId(Integer storeId, Pageable pageable);
    Optional<ProductModel> findByIdAndStoreModelId(Long id, Integer storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductModel p WHERE p.id = :id AND p.storeModel.id = :storeId")
    Optional<ProductModel> findByIdAndStoreModelIdForUpdate(
            @Param("id") Long id,
            @Param("storeId") Integer storeId);
}
