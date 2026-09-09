package com.fooddelivery.ledger.dto;

import com.fooddelivery.common.dto.ledger.BeneficiaryResponse;
import com.fooddelivery.common.dto.ledger.PayoutLineDto;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The frozen payout detail contract the admin screen renders. It is flat rather than nested so the
 * generated client can share one Payout shape across the queue, the history and this screen.
 *
 * <p>It previously carried only id, payee, amount, status and lines, while the UI read
 * displayName, the timeline, the bank reference, the failure reason and the beneficiary -- every
 * one of which came back undefined, so the detail screen rendered almost nothing.
 */
@Data
@Builder
public class PayoutDetailResponse {
    private UUID id;
    private String payeeType;
    private UUID payeeId;
    private String payeeDisplayName;
    private OffsetDateTime periodFrom;
    private OffsetDateTime periodTo;
    private BigDecimal amount;
    private String currency;
    private com.fooddelivery.ledger.entity.PayoutStatus status;
    private BeneficiaryResponse beneficiary;
    private String beneficiarySnapshot;
    private String bankReference;
    private String failureReason;
    private UUID createdBy;
    private UUID approvedBy;
    private UUID paidBy;
    private UUID ledgerTransactionId;
    private UUID settledTransactionId;
    private OffsetDateTime createdAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime paidAt;
    private List<PayoutLineDto> lines;
}
