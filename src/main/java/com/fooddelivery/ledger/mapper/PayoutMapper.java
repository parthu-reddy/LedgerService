package com.fooddelivery.ledger.mapper;

import com.fooddelivery.common.dto.ledger.PayoutDto;
import com.fooddelivery.common.dto.ledger.PayoutLineDto;
import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.entity.PayoutLine;

public class PayoutMapper {

    public static PayoutDto toDto(Payout payout) {
        if (payout == null) return null;
        return PayoutDto.builder()
                .id(payout.getId())
                .payeeType(payout.getPayeeType())
                .payeeId(payout.getPayeeId())
                .payeeDisplayName(payout.getPayeeDisplayName())
                .periodFrom(payout.getPeriodFrom())
                .periodTo(payout.getPeriodTo())
                .amount(payout.getAmount())
                .currency(payout.getCurrency())
                .status(payout.getStatus().name())
                .beneficiarySnapshot(payout.getBeneficiarySnapshot())
                .bankReference(payout.getBankReference())
                .failureReason(payout.getFailureReason())
                .createdBy(payout.getCreatedBy())
                .approvedBy(payout.getApprovedBy())
                .paidBy(payout.getPaidBy())
                .idempotencyKey(payout.getIdempotencyKey())
                .ledgerTransactionId(payout.getLedgerTransactionId())
                .settledTransactionId(payout.getSettledTransactionId())
                .createdAt(payout.getCreatedAt())
                .approvedAt(payout.getApprovedAt())
                .paidAt(payout.getPaidAt())
                .updatedAt(payout.getUpdatedAt())
                .build();
    }

    public static PayoutLineDto toDto(PayoutLine line) {
        if (line == null) return null;
        return PayoutLineDto.builder()
                .id(line.getId())
                .payoutId(line.getPayoutId())
                .ledgerEntryId(line.getLedgerEntryId())
                .referenceId(line.getReferenceId())
                .category(line.getCategory())
                .direction(line.getDirection())
                .amount(line.getAmount())
                .entryCreatedAt(line.getEntryCreatedAt())
                .active(line.isActive())
                .build();
    }
}
