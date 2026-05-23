package id.my.mfikriproject.widuri.erp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;

@MappedSuperclass
@Getter
@SuperBuilder
@NoArgsConstructor
public class AuditableModel {
    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
