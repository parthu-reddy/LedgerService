package com.fooddelivery.ledger.dto;

import com.fooddelivery.common.enums.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public class PayoutSettlementRequest {
    @jakarta.validation.constraints.NotNull
    private UUID ownerId;
    @jakarta.validation.constraints.NotNull
    private AccountType ownerType;
    @jakarta.validation.constraints.NotNull
    private BigDecimal amount;

    @java.lang.SuppressWarnings("all")
    public PayoutSettlementRequest() {
    }

    @java.lang.SuppressWarnings("all")
    public UUID getOwnerId() {
        return this.ownerId;
    }

    @java.lang.SuppressWarnings("all")
    public AccountType getOwnerType() {
        return this.ownerType;
    }

    @java.lang.SuppressWarnings("all")
    public BigDecimal getAmount() {
        return this.amount;
    }

    @java.lang.SuppressWarnings("all")
    public void setOwnerId(final UUID ownerId) {
        this.ownerId = ownerId;
    }

    @java.lang.SuppressWarnings("all")
    public void setOwnerType(final AccountType ownerType) {
        this.ownerType = ownerType;
    }

    @java.lang.SuppressWarnings("all")
    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof PayoutSettlementRequest)) return false;
        final PayoutSettlementRequest other = (PayoutSettlementRequest) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$ownerId = this.getOwnerId();
        final java.lang.Object other$ownerId = other.getOwnerId();
        if (this$ownerId == null ? other$ownerId != null : !this$ownerId.equals(other$ownerId)) return false;
        final java.lang.Object this$ownerType = this.getOwnerType();
        final java.lang.Object other$ownerType = other.getOwnerType();
        if (this$ownerType == null ? other$ownerType != null : !this$ownerType.equals(other$ownerType)) return false;
        final java.lang.Object this$amount = this.getAmount();
        final java.lang.Object other$amount = other.getAmount();
        if (this$amount == null ? other$amount != null : !this$amount.equals(other$amount)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof PayoutSettlementRequest;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $ownerId = this.getOwnerId();
        result = result * PRIME + ($ownerId == null ? 43 : $ownerId.hashCode());
        final java.lang.Object $ownerType = this.getOwnerType();
        result = result * PRIME + ($ownerType == null ? 43 : $ownerType.hashCode());
        final java.lang.Object $amount = this.getAmount();
        result = result * PRIME + ($amount == null ? 43 : $amount.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "PayoutSettlementRequest(ownerId=" + this.getOwnerId() + ", ownerType=" + this.getOwnerType() + ", amount=" + this.getAmount() + ")";
    }
}
