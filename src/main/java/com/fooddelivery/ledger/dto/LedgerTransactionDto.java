package com.fooddelivery.ledger.dto;

import com.fooddelivery.common.enums.ChargeCategory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class LedgerTransactionDto {
    @jakarta.validation.constraints.NotNull
    private UUID transactionId;
    @jakarta.validation.constraints.NotNull
    private ChargeCategory category;
    @jakarta.validation.constraints.NotNull
    private UUID fromAccountId;
    @jakarta.validation.constraints.NotNull
    private UUID toAccountId;
    @jakarta.validation.constraints.NotNull
    private BigDecimal amount;
    @jakarta.validation.constraints.NotNull
    private LocalDateTime date;


    @java.lang.SuppressWarnings("all")
    public static class LedgerTransactionDtoBuilder {
        @java.lang.SuppressWarnings("all")
        private UUID transactionId;
        @java.lang.SuppressWarnings("all")
        private ChargeCategory category;
        @java.lang.SuppressWarnings("all")
        private UUID fromAccountId;
        @java.lang.SuppressWarnings("all")
        private UUID toAccountId;
        @java.lang.SuppressWarnings("all")
        private BigDecimal amount;
        @java.lang.SuppressWarnings("all")
        private LocalDateTime date;

        @java.lang.SuppressWarnings("all")
        LedgerTransactionDtoBuilder() {
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerTransactionDto.LedgerTransactionDtoBuilder transactionId(final UUID transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerTransactionDto.LedgerTransactionDtoBuilder category(final ChargeCategory category) {
            this.category = category;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerTransactionDto.LedgerTransactionDtoBuilder fromAccountId(final UUID fromAccountId) {
            this.fromAccountId = fromAccountId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerTransactionDto.LedgerTransactionDtoBuilder toAccountId(final UUID toAccountId) {
            this.toAccountId = toAccountId;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerTransactionDto.LedgerTransactionDtoBuilder amount(final BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        /**
         * @return {@code this}.
         */
        @java.lang.SuppressWarnings("all")
        public LedgerTransactionDto.LedgerTransactionDtoBuilder date(final LocalDateTime date) {
            this.date = date;
            return this;
        }

        @java.lang.SuppressWarnings("all")
        public LedgerTransactionDto build() {
            return new LedgerTransactionDto(this.transactionId, this.category, this.fromAccountId, this.toAccountId, this.amount, this.date);
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "LedgerTransactionDto.LedgerTransactionDtoBuilder(transactionId=" + this.transactionId + ", category=" + this.category + ", fromAccountId=" + this.fromAccountId + ", toAccountId=" + this.toAccountId + ", amount=" + this.amount + ", date=" + this.date + ")";
        }
    }

    @java.lang.SuppressWarnings("all")
    public static LedgerTransactionDto.LedgerTransactionDtoBuilder builder() {
        return new LedgerTransactionDto.LedgerTransactionDtoBuilder();
    }

    @java.lang.SuppressWarnings("all")
    public UUID getTransactionId() {
        return this.transactionId;
    }

    @java.lang.SuppressWarnings("all")
    public ChargeCategory getCategory() {
        return this.category;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getFromAccountId() {
        return this.fromAccountId;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getToAccountId() {
        return this.toAccountId;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getAmount() {
        return this.amount;
    }

    @java.lang.SuppressWarnings("all")
    public LocalDateTime getDate() {
        return this.date;
    }

    @java.lang.SuppressWarnings("all")
    public void setTransactionId(final UUID transactionId) {
        this.transactionId = transactionId;
    }

    @java.lang.SuppressWarnings("all")
    public void setCategory(final ChargeCategory category) {
        this.category = category;
    }

    @java.lang.SuppressWarnings("all")
    public void setFromAccountId(final UUID fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    @java.lang.SuppressWarnings("all")
    public void setToAccountId(final UUID toAccountId) {
        this.toAccountId = toAccountId;
    }

    @java.lang.SuppressWarnings("all")
    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    @java.lang.SuppressWarnings("all")
    public void setDate(final LocalDateTime date) {
        this.date = date;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof LedgerTransactionDto)) return false;
        final LedgerTransactionDto other = (LedgerTransactionDto) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$transactionId = this.getTransactionId();
        final java.lang.Object other$transactionId = other.getTransactionId();
        if (this$transactionId == null ? other$transactionId != null : !this$transactionId.equals(other$transactionId)) return false;
        final java.lang.Object this$category = this.getCategory();
        final java.lang.Object other$category = other.getCategory();
        if (this$category == null ? other$category != null : !this$category.equals(other$category)) return false;
        final java.lang.Object this$fromAccountId = this.getFromAccountId();
        final java.lang.Object other$fromAccountId = other.getFromAccountId();
        if (this$fromAccountId == null ? other$fromAccountId != null : !this$fromAccountId.equals(other$fromAccountId)) return false;
        final java.lang.Object this$toAccountId = this.getToAccountId();
        final java.lang.Object other$toAccountId = other.getToAccountId();
        if (this$toAccountId == null ? other$toAccountId != null : !this$toAccountId.equals(other$toAccountId)) return false;
        final java.lang.Object this$amount = this.getAmount();
        final java.lang.Object other$amount = other.getAmount();
        if (this$amount == null ? other$amount != null : !this$amount.equals(other$amount)) return false;
        final java.lang.Object this$date = this.getDate();
        final java.lang.Object other$date = other.getDate();
        if (this$date == null ? other$date != null : !this$date.equals(other$date)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof LedgerTransactionDto;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $transactionId = this.getTransactionId();
        result = result * PRIME + ($transactionId == null ? 43 : $transactionId.hashCode());
        final java.lang.Object $category = this.getCategory();
        result = result * PRIME + ($category == null ? 43 : $category.hashCode());
        final java.lang.Object $fromAccountId = this.getFromAccountId();
        result = result * PRIME + ($fromAccountId == null ? 43 : $fromAccountId.hashCode());
        final java.lang.Object $toAccountId = this.getToAccountId();
        result = result * PRIME + ($toAccountId == null ? 43 : $toAccountId.hashCode());
        final java.lang.Object $amount = this.getAmount();
        result = result * PRIME + ($amount == null ? 43 : $amount.hashCode());
        final java.lang.Object $date = this.getDate();
        result = result * PRIME + ($date == null ? 43 : $date.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "LedgerTransactionDto(transactionId=" + this.getTransactionId() + ", category=" + this.getCategory() + ", fromAccountId=" + this.getFromAccountId() + ", toAccountId=" + this.getToAccountId() + ", amount=" + this.getAmount() + ", date=" + this.getDate() + ")";
    }

    @java.lang.SuppressWarnings("all")
    public LedgerTransactionDto() {
    }

    @java.lang.SuppressWarnings("all")
    public LedgerTransactionDto(final UUID transactionId, final ChargeCategory category, final UUID fromAccountId, final UUID toAccountId, final BigDecimal amount, final LocalDateTime date) {
        this.transactionId = transactionId;
        this.category = category;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.date = date;
    }
}
