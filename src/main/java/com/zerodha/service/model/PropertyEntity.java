package com.zerodha.service.model;

import com.zerodha.service.enums.PropertyStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Generic key/value runtime configuration backed by the {@code properties}
 * table. Lets us tune behaviour (e.g. the per-stock allocation cap) without a
 * redeploy. {@code miscInfo} holds free-form JSONB metadata (notes, audit, etc).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "properties",
        indexes = {
                @Index(name = "idx_properties_name_status", columnList = "name, status")
        }
)
public class PropertyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "value", length = 512)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PropertyStatusEnum status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "misc_info", columnDefinition = "jsonb")
    private String miscInfo;
}
