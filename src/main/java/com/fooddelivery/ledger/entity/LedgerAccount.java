package com.fooddelivery.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

import com.fooddelivery.common.enums.AccountType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "ledger_accounts", uniqueConstraints = {
    @jakarta.persistence.UniqueConstraint(columnNames = {"owner_id", "owner_type"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerAccount {
    @Id
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type")
    private AccountType ownerType;
    @Column(name = "owner_id")
    private UUID ownerId;
    
    @Column(name = "balance")
    private BigDecimal balance;

    @Version
    @Column(name = "lock_version")
    private Integer lockVersion;
}
