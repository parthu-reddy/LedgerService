package com.fooddelivery.ledger.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A ledger movement the service refused, as an operator needs to see it.
 *
 * <p>Carries the payload so the transaction can be understood and, once the cause is fixed,
 * replayed by its producer. The rejection reason is the ledger's own message -- a non-derivable
 * transaction id, an unbalanced leg, insufficient funds, or a retry exhausted on the DLT.
 */
@Data
@Builder
public class LedgerRejectionDto {
    private UUID id;
    private String eventId;
    private String producer;
    private String reason;
    private String payload;
    private OffsetDateTime createdAt;
    private OffsetDateTime resolvedAt;
    private String resolvedBy;
    private String resolutionNote;
    /** How long this has been sitting unresolved. Anything over an hour raises a STUCK break. */
    private Long ageMinutes;
}
