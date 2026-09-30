package com.fooddelivery.ledger.service;

import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.ledger.entity.LedgerRejection;
import com.fooddelivery.ledger.repository.ILedgerRejectionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a terminal, semantic ledger rejection after the failed booking transaction has rolled
 * back.
 *
 * <p>A {@code LedgerRejectedException} can happen after the booking path has changed managed
 * state. It must therefore roll that booking back. This separate transaction then records the
 * operator-visible reason and the terminal event key together, so Kafka does not retry a business
 * rejection or replace it with a generic DLT row.
 */
@Service
@RequiredArgsConstructor
public class LedgerRejectionRecorder {

    private final IIdempotencyKeyRepository idempotencyKeyRepository;
    private final ILedgerRejectionRepository rejectionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String processedEventKey, String eventId, String producer, String payload, String reason) {
        // Claim the terminal key in the database instead of read-then-insert. Concurrent Kafka
        // redeliveries can both arrive after the booking transaction rolls its original claim
        // back; only one is allowed to create the rejection record.
        if (idempotencyKeyRepository.tryClaim(processedEventKey) == 0) {
            return;
        }

        rejectionRepository.save(LedgerRejection.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .producer(producer)
                .payload(payload)
                .reason(reason)
                .createdAt(Instant.now())
                .build());
    }
}
