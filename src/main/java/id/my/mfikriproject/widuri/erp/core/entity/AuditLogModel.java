package id.my.mfikriproject.widuri.erp.core.entity;

import id.my.mfikriproject.widuri.erp.core.enums.ActionEnum;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Entity
@Table(name = "sys_audit_log",
indexes = {
        @Index(name = "idx_sys_audit_log_record", columnList = "table_name, record_id")
})
@Immutable
public class AuditLogModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false)
    private String tableName;

    @Column(nullable = false)
    private Long recordId;

    @Enumerated(EnumType.STRING)
    private ActionEnum action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> oldData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> newData;

    @Column(length = 100)
    private String changedBy;

    private OffsetDateTime changedAt;
}
