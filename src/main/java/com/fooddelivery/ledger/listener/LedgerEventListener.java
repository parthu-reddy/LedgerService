package com.fooddelivery.ledger.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.enums.AccountType;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.util.KafkaHeaderUtils;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
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
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@lombok.extern.slf4j.Slf4j
public class LedgerEventListener {

    private final DoubleEntryLedgerService ledgerService;
    private final ObjectMapper objectMapper;
    private final org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;
    private final IIdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionTemplate transactionTemplate;

    public LedgerEventListener(DoubleEntryLedgerService ledgerService, ObjectMapper objectMapper, org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate, IIdempotencyKeyRepository idempotencyKeyRepository, TransactionTemplate transactionTemplate) {
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 1000, multiplier = 2.0), autoCreateTopics = "true")
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
                    handleLedgerTransactionRequest(payload);
                } else if ("LEDGER_BULK_TRANSACTION_REQUEST".equals(eventTypeStr)) {
                    handleLedgerBulkTransactionRequest(payload);
                }
                // Handle ORDER_PAID or other events if needed
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @DltHandler
    public void handleDltEvent(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Headers Map<String, Object> headers) {
        log.error("Event failed after all retries. Payload: {}, Topic: {}, Headers: {}", payload, topic, headers);
        // Publish failure event for saga rollback
        kafkaTemplate.send(KafkaConstants.TOPIC_LEDGER_EVENTS_DLQ, payload);
    }

    private void handleLedgerTransactionRequest(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        UUID transferId = UUID.fromString(node.get("transferId").asText());
        UUID referenceId = null;
        if (node.has("referenceId")) {
            referenceId = UUID.fromString(node.get("referenceId").asText());
        }
        UUID fromId = UUID.fromString(node.get("fromId").asText());
        AccountType fromType = AccountType.valueOf(node.get("fromType").asText());
        UUID toId = UUID.fromString(node.get("toId").asText());
        AccountType toType = AccountType.valueOf(node.get("toType").asText());
        BigDecimal amount = new BigDecimal(node.get("amount").asText());
        ChargeCategory category = null;
        if (node.has("chargeCategory")) {
            category = ChargeCategory.valueOf(node.get("chargeCategory").asText());
        }
        ledgerService.recordTransaction(transferId, referenceId, fromId, fromType, toId, toType, amount, category);
        log.info("Successfully processed LEDGER_TRANSACTION_REQUEST for transferId: {}", transferId);
    }

    private void handleLedgerBulkTransactionRequest(String payload) throws Exception {
        com.fooddelivery.common.dto.LedgerBulkTransactionRequest request = objectMapper.readValue(payload, com.fooddelivery.common.dto.LedgerBulkTransactionRequest.class);
        ledgerService.recordBulkTransaction(request.getReferenceId(), request.getEntries());
        log.info("Successfully processed LEDGER_BULK_TRANSACTION_REQUEST for referenceId: {}", request.getReferenceId());
    }
}
