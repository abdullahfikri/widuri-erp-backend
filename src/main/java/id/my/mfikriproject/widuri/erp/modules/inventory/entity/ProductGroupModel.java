package id.my.mfikriproject.widuri.erp.modules.inventory.entity;

import id.my.mfikriproject.widuri.erp.core.entity.AuditableModel;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(
        name = "m_product_group",
        // Constraint ini hanya mencakup baris dengan brand IS NOT NULL.
        // Kasus brand IS NULL ditangani oleh partial index uq_product_group_name_null_brand
        // di V2__Add_product_group_unique_constraints.sql — lihat juga isDuplicate() di service layer.
        uniqueConstraints = @UniqueConstraint(
                name = "uq_product_group_name_brand",
                columnNames = {"name", "brand"}
        )
)
public class ProductGroupModel extends AuditableModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(length = 100)
    private String brand;

    @Column(length = 50)
    private String category;

    @Size(max = 2000)
    @Column(columnDefinition = "text")
    private String description;

    @OneToMany(mappedBy = "productGroupModel", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<ProductModel> productList;

    public void updateFields(String name, String brand, String category, String description) {
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.description = description;
    }
}
