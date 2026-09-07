package com.fooddelivery.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;
import com.fooddelivery.common.enums.LedgerAccountType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ledger_accounts", uniqueConstraints = {@jakarta.persistence.UniqueConstraint(columnNames = {"owner_type", "owner_id"})})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerAccount {
    @Id
    @Column(name = "id")
    @jakarta.validation.constraints.NotNull
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    @jakarta.validation.constraints.NotNull
    private LedgerAccountType ownerType;

    @Column(name = "owner_id", nullable = false)
    @jakarta.validation.constraints.NotNull
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private LedgerAccountType.Kind kind;

    @Column(name = "balance", nullable = false)
    @jakarta.validation.constraints.NotNull
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
        @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    private String currency = "INR";

    @Version
    @Column(name = "lock_version", nullable = false)
    @jakarta.validation.constraints.NotNull
    private Integer lockVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
