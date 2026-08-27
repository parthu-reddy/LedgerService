package com.fooddelivery.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;
import com.fooddelivery.common.enums.AccountType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "ledger_accounts", uniqueConstraints = {@jakarta.persistence.UniqueConstraint(columnNames = {"owner_id", "owner_type"})})
public class LedgerAccount {
    @Id
    @Column(name = "id")
    @jakarta.validation.constraints.NotNull
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type")
    @jakarta.validation.constraints.NotNull
    private AccountType ownerType;
    @Column(name = "owner_id")
    @jakarta.validation.constraints.NotNull
    private UUID ownerId;
    @Column(name = "balance")
    @jakarta.validation.constraints.NotNull
    private BigDecimal balance;
    @Version
    @Column(name = "lock_version")
    @jakarta.validation.constraints.NotNull
    private Integer lockVersion;


    @java.lang.SuppressWarnings("all")
    public static class LedgerAccountBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID id;
        @java.lang.SuppressWarnings("all")
        private AccountType ownerType;
        @java.lang.SuppressWarnings("all")
        private UUID ownerId;
        @java.lang.SuppressWarnings("all")
        private BigDecimal balance;
        @java.lang.SuppressWarnings("all")
        private Integer lockVersion;

        @java.lang.SuppressWarnings("all")
        LedgerAccountBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerAccount.LedgerAccountBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerAccount.LedgerAccountBuilder ownerType(final AccountType ownerType) {
            this.ownerType = ownerType;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerAccount.LedgerAccountBuilder ownerId(final UUID ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerAccount.LedgerAccountBuilder balance(final BigDecimal balance) {
            this.balance = balance;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerAccount.LedgerAccountBuilder lockVersion(final Integer lockVersion) {
            this.lockVersion = lockVersion;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public LedgerAccount build() {
            return new LedgerAccount(this.id, this.ownerType, this.ownerId, this.balance, this.lockVersion);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "LedgerAccount.LedgerAccountBuilder(id=" + this.id + ", ownerType=" + this.ownerType + ", ownerId=" + this.ownerId + ", balance=" + this.balance + ", lockVersion=" + this.lockVersion + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static LedgerAccount.LedgerAccountBuilder builder() {
        return new LedgerAccount.LedgerAccountBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public UUID getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public AccountType getOwnerType() {
        return this.ownerType;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getOwnerId() {
        return this.ownerId;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getBalance() {
        return this.balance;
    }

    @java.lang.SuppressWarnings("all")
    public Integer getLockVersion() {
        return this.lockVersion;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final UUID id) {
        this.id = id;
    }

    @java.lang.SuppressWarnings("all")
    public void setOwnerType(final AccountType ownerType) {
        this.ownerType = ownerType;
    }

    @java.lang.SuppressWarnings("all")
    public void setOwnerId(final UUID ownerId) {
        this.ownerId = ownerId;
    }

    @java.lang.SuppressWarnings("all")
    public void setBalance(final BigDecimal balance) {
        this.balance = balance;
    }

    @java.lang.SuppressWarnings("all")
    public void setLockVersion(final Integer lockVersion) {
        this.lockVersion = lockVersion;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof LedgerAccount)) return false;
        final LedgerAccount other = (LedgerAccount) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$lockVersion = this.getLockVersion();
        final java.lang.Object other$lockVersion = other.getLockVersion();
        if (this$lockVersion == null ? other$lockVersion != null : !this$lockVersion.equals(other$lockVersion)) return false;
        final java.lang.Object this$id = this.getId();
        final java.lang.Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final java.lang.Object this$ownerType = this.getOwnerType();
        final java.lang.Object other$ownerType = other.getOwnerType();
        if (this$ownerType == null ? other$ownerType != null : !this$ownerType.equals(other$ownerType)) return false;
        final java.lang.Object this$ownerId = this.getOwnerId();
        final java.lang.Object other$ownerId = other.getOwnerId();
        if (this$ownerId == null ? other$ownerId != null : !this$ownerId.equals(other$ownerId)) return false;
        final java.lang.Object this$balance = this.getBalance();
        final java.lang.Object other$balance = other.getBalance();
        if (this$balance == null ? other$balance != null : !this$balance.equals(other$balance)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof LedgerAccount;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $lockVersion = this.getLockVersion();
        result = result * PRIME + ($lockVersion == null ? 43 : $lockVersion.hashCode());
        final java.lang.Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final java.lang.Object $ownerType = this.getOwnerType();
        result = result * PRIME + ($ownerType == null ? 43 : $ownerType.hashCode());
        final java.lang.Object $ownerId = this.getOwnerId();
        result = result * PRIME + ($ownerId == null ? 43 : $ownerId.hashCode());
        final java.lang.Object $balance = this.getBalance();
        result = result * PRIME + ($balance == null ? 43 : $balance.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "LedgerAccount(id=" + this.getId() + ", ownerType=" + this.getOwnerType() + ", ownerId=" + this.getOwnerId() + ", balance=" + this.getBalance() + ", lockVersion=" + this.getLockVersion() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public LedgerAccount() {
    }

    @java.lang.SuppressWarnings("all")
    public LedgerAccount(final UUID id, final AccountType ownerType, final UUID ownerId, final BigDecimal balance, final Integer lockVersion) {
        this.id = id;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.balance = balance;
        this.lockVersion = lockVersion;
    }
}
