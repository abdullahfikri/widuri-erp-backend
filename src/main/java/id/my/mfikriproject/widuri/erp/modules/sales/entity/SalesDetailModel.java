package id.my.mfikriproject.widuri.erp.modules.sales.entity;

import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductModel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Entity
@Table(name = "t_sales_detail",
        indexes = {
                @Index(name = "idx_t_sales_detail_sales_id", columnList = "sales_id"),
                @Index(name = "idx_t_sales_detail_product_id", columnList = "product_id")
        }
)
public class SalesDetailModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_id", nullable = false)
    private SalesModel salesModel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductModel productModel;

    @Column(nullable = false)
    private Integer quantity;

    // Snapshot HPP saat transaksi — diisi service dari ProductModel.basePrice, bukan dari client
    @Column(nullable = false)
    private BigDecimal costPriceAtTime;

    @Column(nullable = false)
    private BigDecimal soldPriceAtTime;

    // Computed server-side: quantity * soldPriceAtTime — tidak diterima dari client
    @Column(nullable = false)
    private BigDecimal subtotal;
}
