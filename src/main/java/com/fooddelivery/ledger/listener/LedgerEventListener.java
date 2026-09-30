package com.fooddelivery.ledger.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.exception.LedgerRejectedException;
import com.fooddelivery.common.util.KafkaHeaderUtils;
import com.fooddelivery.ledger.entity.LedgerRejection;
import com.fooddelivery.ledger.repository.ILedgerRejectionRepository;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import com.fooddelivery.ledger.service.LedgerRejectionRecorder;
import com.fooddelivery.common.entity.IdempotencyKey;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;

import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class LedgerEventListener {

    private final DoubleEntryLedgerService ledgerService;
    private final ObjectMapper objectMapper;
    private final IIdempotencyKeyRepository idempotencyKeyRepository;
    private final ILedgerRejectionRepository rejectionRepository;
    private final LedgerRejectionRecorder rejectionRecorder;
    private final TransactionTemplate transactionTemplate;
    private final com.fooddelivery.common.event.EventBinder eventBinder;

    public LedgerEventListener(DoubleEntryLedgerService ledgerService,
                               ObjectMapper objectMapper,
                               IIdempotencyKeyRepository idempotencyKeyRepository,
                               ILedgerRejectionRepository rejectionRepository,
                               LedgerRejectionRecorder rejectionRecorder,
                               TransactionTemplate transactionTemplate,
                               com.fooddelivery.common.event.EventBinder eventBinder) {
        this.eventBinder = eventBinder;
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.rejectionRepository = rejectionRepository;
        this.rejectionRecorder = rejectionRecorder;
        this.transactionTemplate = transactionTemplate;
    }

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 1000, multiplier = 2.0), autoCreateTopics = "true", exclude = {com.fooddelivery.common.event.EventBindingException.class}, traversingCauses = "true")
    @KafkaListener(topics = KafkaConstants.TOPIC_LEDGER_EVENTS, groupId = KafkaConstants.GROUP_LEDGER_SERVICE + "-ledgereventlistener")
    public void handleEvents(String payload, @Headers Map<String, Object> headers) throws Exception {
        String extractedEventId = KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
        final String resolvedEventId;
        if (extractedEventId == null) {
            resolvedEventId = UUID.nameUUIDFromBytes(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        } else {
            resolvedEventId = extractedEventId;
        }

        String idempotencyKeyStr = "processed_event:" + resolvedEventId;

        try {
            transactionTemplate.execute(status -> {
                if (idempotencyKeyRepository.existsById(idempotencyKeyStr)) {
                    log.info("Duplicate ledger event ignored: {}", idempotencyKeyStr);
                    return null;
                }
                idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKeyStr));

                try {
                    JsonNode rootNode = objectMapper.readTree(payload);
                    String eventTypeStr = KafkaHeaderUtils.extractEventType(headers, rootNode);
                    if (EventType.LEDGER_TRANSACTION_REQUEST.name().equals(eventTypeStr)) {
                        LedgerTransactionCommand cmd = eventBinder.bind(payload, LedgerTransactionCommand.class);
                        try {
                            ledgerService.record(cmd);
                        } catch (LedgerRejectedException rejection) {
                            // Do not catch this inside the booking transaction and save there:
                            // ledgerService has already marked that transaction rollback-only.
                            throw new SemanticLedgerRejection(cmd, rejection);
                        }
                    }
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                }
                return null;
            });
        } catch (SemanticLedgerRejection rejection) {
            LedgerTransactionCommand command = rejection.command();
            log.warn("Ledger transaction {} rejected: {}", command.getTransactionId(), rejection.getCause().getMessage());
            rejectionRecorder.record(idempotencyKeyStr, resolvedEventId, command.getProducer(), payload,
                    rejection.getCause().getMessage());
        }
    }

    @DltHandler
    public void handleDltEvent(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Headers Map<String, Object> headers) {
        log.error("Event failed after all retries. Payload: {}, Topic: {}, Headers: {} replay={}", payload, topic, headers,
                KafkaHeaderUtils.deadLetterPosition(headers));
        String extractedEventId = KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
        String eventId = extractedEventId != null ? extractedEventId : "UNKNOWN_DLT";
        
        LedgerRejection rejection = LedgerRejection.builder()
            .id(UUID.randomUUID())
            .eventId(eventId)
            .producer("DLT")
            .payload(payload)
            .reason("Failed after all retries")
            .createdAt(Instant.now())
            .build();
        rejectionRepository.save(rejection);
    }

    /** Carries the parsed command outside the transaction that must be rolled back. */
    private static final class SemanticLedgerRejection extends RuntimeException {
        private final LedgerTransactionCommand command;

        private SemanticLedgerRejection(LedgerTransactionCommand command, LedgerRejectedException cause) {
            super(cause);
            this.command = command;
        }

        private LedgerTransactionCommand command() {
            return command;
        }

        @Override
        public LedgerRejectedException getCause() {
            return (LedgerRejectedException) super.getCause();
        }
    }
}
