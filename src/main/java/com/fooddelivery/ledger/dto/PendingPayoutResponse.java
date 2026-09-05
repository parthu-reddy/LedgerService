package com.fooddelivery.ledger.dto;

import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.common.dto.ledger.BeneficiaryResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingPayoutResponse {
    private String payeeType;
    private UUID payeeId;
    private String displayName;
    private BigDecimal unsettledAmount;
    private OffsetDateTime unsettledSince;
    private int lineCount;
    private Payout lastPayout;
    private BeneficiaryResponse beneficiaryStatus;
}
