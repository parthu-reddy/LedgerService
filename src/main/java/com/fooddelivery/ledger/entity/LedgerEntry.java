package com.fooddelivery.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", uniqueConstraints = {@jakarta.persistence.UniqueConstraint(columnNames = {"transaction_id", "direction"})})
public class LedgerEntry {
    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "transaction_id")
    private UUID transactionId;
    @Column(name = "account_id")
    private UUID accountId;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "direction")
    private com.fooddelivery.common.enums.TransactionDirection direction; // CREDIT, DEBIT
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "category", nullable = false)
    private com.fooddelivery.common.enums.ChargeCategory category;
    @Column(name = "amount")
    private BigDecimal amount;
    @Column(name = "created_at")
    private LocalDateTime createdAt;


    @java.lang.SuppressWarnings("all")
    public static class LedgerEntryBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID id;
        @java.lang.SuppressWarnings("all")
        private UUID transactionId;
        @java.lang.SuppressWarnings("all")
        private UUID accountId;
        @java.lang.SuppressWarnings("all")
        private com.fooddelivery.common.enums.TransactionDirection direction;
        @java.lang.SuppressWarnings("all")
        private com.fooddelivery.common.enums.ChargeCategory category;
        @java.lang.SuppressWarnings("all")
        private BigDecimal amount;
        @java.lang.SuppressWarnings("all")
        private LocalDateTime createdAt;

        @java.lang.SuppressWarnings("all")
        LedgerEntryBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerEntry.LedgerEntryBuilder id(final UUID id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerEntry.LedgerEntryBuilder transactionId(final UUID transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerEntry.LedgerEntryBuilder accountId(final UUID accountId) {
            this.accountId = accountId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerEntry.LedgerEntryBuilder direction(final com.fooddelivery.common.enums.TransactionDirection direction) {
            this.direction = direction;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerEntry.LedgerEntryBuilder category(final com.fooddelivery.common.enums.ChargeCategory category) {
            this.category = category;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerEntry.LedgerEntryBuilder amount(final BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerEntry.LedgerEntryBuilder createdAt(final LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public LedgerEntry build() {
            return new LedgerEntry(this.id, this.transactionId, this.accountId, this.direction, this.category, this.amount, this.createdAt);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "LedgerEntry.LedgerEntryBuilder(id=" + this.id + ", transactionId=" + this.transactionId + ", accountId=" + this.accountId + ", direction=" + this.direction + ", category=" + this.category + ", amount=" + this.amount + ", createdAt=" + this.createdAt + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static LedgerEntry.LedgerEntryBuilder builder() {
        return new LedgerEntry.LedgerEntryBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public UUID getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getTransactionId() {
        return this.transactionId;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getAccountId() {
        return this.accountId;
    }

    @java.lang.SuppressWarnings("all")
    public com.fooddelivery.common.enums.TransactionDirection getDirection() {
        return this.direction;
    }

    @java.lang.SuppressWarnings("all")
    public com.fooddelivery.common.enums.ChargeCategory getCategory() {
        return this.category;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getAmount() {
        return this.amount;
    }

    @java.lang.SuppressWarnings("all")
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final UUID id) {
        this.id = id;
    }

    @java.lang.SuppressWarnings("all")
    public void setTransactionId(final UUID transactionId) {
        this.transactionId = transactionId;
    }

    @java.lang.SuppressWarnings("all")
    public void setAccountId(final UUID accountId) {
        this.accountId = accountId;
    }

    @java.lang.SuppressWarnings("all")
    public void setDirection(final com.fooddelivery.common.enums.TransactionDirection direction) {
        this.direction = direction;
    }

    @java.lang.SuppressWarnings("all")
    public void setCategory(final com.fooddelivery.common.enums.ChargeCategory category) {
        this.category = category;
    }

    @java.lang.SuppressWarnings("all")
    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    @java.lang.SuppressWarnings("all")
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof LedgerEntry)) return false;
        final LedgerEntry other = (LedgerEntry) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$id = this.getId();
        final java.lang.Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final java.lang.Object this$transactionId = this.getTransactionId();
        final java.lang.Object other$transactionId = other.getTransactionId();
        if (this$transactionId == null ? other$transactionId != null : !this$transactionId.equals(other$transactionId)) return false;
        final java.lang.Object this$accountId = this.getAccountId();
        final java.lang.Object other$accountId = other.getAccountId();
        if (this$accountId == null ? other$accountId != null : !this$accountId.equals(other$accountId)) return false;
        final java.lang.Object this$direction = this.getDirection();
        final java.lang.Object other$direction = other.getDirection();
        if (this$direction == null ? other$direction != null : !this$direction.equals(other$direction)) return false;
        final java.lang.Object this$category = this.getCategory();
        final java.lang.Object other$category = other.getCategory();
        if (this$category == null ? other$category != null : !this$category.equals(other$category)) return false;
        final java.lang.Object this$amount = this.getAmount();
        final java.lang.Object other$amount = other.getAmount();
        if (this$amount == null ? other$amount != null : !this$amount.equals(other$amount)) return false;
        final java.lang.Object this$createdAt = this.getCreatedAt();
        final java.lang.Object other$createdAt = other.getCreatedAt();
        if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof LedgerEntry;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final java.lang.Object $transactionId = this.getTransactionId();
        result = result * PRIME + ($transactionId == null ? 43 : $transactionId.hashCode());
        final java.lang.Object $accountId = this.getAccountId();
        result = result * PRIME + ($accountId == null ? 43 : $accountId.hashCode());
        final java.lang.Object $direction = this.getDirection();
        result = result * PRIME + ($direction == null ? 43 : $direction.hashCode());
        final java.lang.Object $category = this.getCategory();
        result = result * PRIME + ($category == null ? 43 : $category.hashCode());
        final java.lang.Object $amount = this.getAmount();
        result = result * PRIME + ($amount == null ? 43 : $amount.hashCode());
        final java.lang.Object $createdAt = this.getCreatedAt();
        result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "LedgerEntry(id=" + this.getId() + ", transactionId=" + this.getTransactionId() + ", accountId=" + this.getAccountId() + ", direction=" + this.getDirection() + ", category=" + this.getCategory() + ", amount=" + this.getAmount() + ", createdAt=" + this.getCreatedAt() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public LedgerEntry() {
    }

    @java.lang.SuppressWarnings("all")
    public LedgerEntry(final UUID id, final UUID transactionId, final UUID accountId, final com.fooddelivery.common.enums.TransactionDirection direction, final com.fooddelivery.common.enums.ChargeCategory category, final BigDecimal amount, final LocalDateTime createdAt) {
        this.id = id;
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.direction = direction;
        this.category = category;
        this.amount = amount;
        this.createdAt = createdAt;
    }
}
