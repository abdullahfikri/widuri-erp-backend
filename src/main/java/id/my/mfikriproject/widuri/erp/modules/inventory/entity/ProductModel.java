package id.my.mfikriproject.widuri.erp.modules.inventory.entity;

import id.my.mfikriproject.widuri.erp.core.entity.AuditableModel;
import id.my.mfikriproject.widuri.erp.core.entity.StoreModel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(name = "m_product",
        uniqueConstraints = @UniqueConstraint(name = "uq_m_product_sku", columnNames = "sku"),
        indexes = {
                @Index(name = "idx_m_product_group_id", columnList = "product_group_id"),
                @Index(name = "idx_m_product_store_id", columnList = "store_id")
        }
)
public class ProductModel extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_group_id", nullable = false)
    private ProductGroupModel productGroupModel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreModel storeModel;

    @Column(length = 50, nullable = false)
    private String sku;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attributes;

    @Column(nullable = false)
    private BigDecimal basePrice;

    @Column(nullable = false)
    private BigDecimal labelPrice;

    @Column(nullable = false)
    private BigDecimal floorPrice;

    // Field-level setter only — must be accessed via @Lock(PESSIMISTIC_WRITE) repository query
    @Setter(AccessLevel.PACKAGE)
    private Integer stockQuantity;

    private Integer minStockLevel;

    public void updateFields(Map<String, Object> attributes,
                              BigDecimal basePrice, BigDecimal labelPrice, BigDecimal floorPrice,
                              Integer minStockLevel) {
        this.attributes = attributes != null ? attributes : java.util.Collections.emptyMap();
        this.basePrice = basePrice;
        this.labelPrice = labelPrice;
        this.floorPrice = floorPrice;
        if (minStockLevel != null) this.minStockLevel = minStockLevel;
    }

    public void addStock(int delta) {
        int current = this.stockQuantity != null ? this.stockQuantity : 0;
        this.stockQuantity = current + delta;
    }

    public void subtractStock(int quantity) {
        int current = this.stockQuantity != null ? this.stockQuantity : 0;
        if (current < quantity) {
            throw new IllegalArgumentException(
                    "Insufficient stock: " + current + " available, " + quantity + " requested");
        }
        this.stockQuantity = current - quantity;
    }
}
