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
}
