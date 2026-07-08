package id.my.mfikriproject.widuri.erp.modules.sales.entity;

import id.my.mfikriproject.widuri.erp.core.entity.StoreModel;
import id.my.mfikriproject.widuri.erp.modules.sales.converter.PaymentMethodConverter;
import id.my.mfikriproject.widuri.erp.modules.sales.enums.PaymentMethodEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Entity
@Table(name = "t_sales",
        indexes = {
                @Index(name = "idx_t_sales_store_id", columnList = "store_id"),
                @Index(name = "idx_t_sales_transaction_date", columnList = "transaction_date")
        },
        uniqueConstraints = @UniqueConstraint(name = "uq_t_sales_invoice_number", columnNames = "invoice_number")
)
public class SalesModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreModel storeModel;

    @Column(length = 50, nullable = false)
    private String invoiceNumber;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Convert(converter = PaymentMethodConverter.class)
    @Column(length = 20, nullable = false)
    private PaymentMethodEnum paymentMethod;

    // Set by service to OffsetDateTime.now() — never accepted from client input
    @Column(updatable = false)
    private OffsetDateTime transactionDate;

    @OneToMany(mappedBy = "salesModel", cascade = CascadeType.PERSIST)
    List<SalesDetailModel> details;
}
