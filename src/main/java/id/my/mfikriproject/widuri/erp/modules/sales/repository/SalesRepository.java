package id.my.mfikriproject.widuri.erp.modules.sales.repository;

import id.my.mfikriproject.widuri.erp.modules.sales.entity.SalesModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface SalesRepository extends JpaRepository<SalesModel, Long> {

    /** Paginated sales for a store within a date range, newest first. */
    @Query("SELECT s FROM SalesModel s " +
           "WHERE s.storeModel.id = :storeId " +
           "AND s.transactionDate >= :from AND s.transactionDate < :to " +
           "ORDER BY s.transactionDate DESC")
    Page<SalesModel> findByStoreAndDateRange(
            @Param("storeId") Integer storeId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    /**
     * Single transaction by store-scoped invoice number.
     * Eager-fetches details → product → product-group to avoid N+1
     * when building the detail response DTO.
     */
    @EntityGraph(attributePaths = {
            "details",
            "details.productModel",
            "details.productModel.productGroupModel"
    })
    Optional<SalesModel> findByStoreModelIdAndInvoiceNumber(
            Integer storeId, String invoiceNumber);
}
