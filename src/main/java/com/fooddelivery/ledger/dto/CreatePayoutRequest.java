package com.fooddelivery.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayoutRequest {
    private String payeeType;
    private UUID payeeId;
    private OffsetDateTime periodTo;
    private boolean force;
}
