package com.fooddelivery.ledger.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.enums.AccountType;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.util.KafkaHeaderUtils;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class LedgerEventListener {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LedgerEventListener.class);
    private final DoubleEntryLedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 1000, multiplier = 2.0), autoCreateTopics = "true")
    @KafkaListener(topics = KafkaConstants.TOPIC_LEDGER_EVENTS, groupId = KafkaConstants.GROUP_LEDGER_SERVICE)
    public void handleEvents(String payload, @Headers Map<String, Object> headers) throws Exception {
        JsonNode rootNode = objectMapper.readTree(payload);
        String eventTypeStr = KafkaHeaderUtils.extractEventType(headers, rootNode);
        if (EventType.LEDGER_TRANSACTION_REQUEST.name().equals(eventTypeStr)) {
            handleLedgerTransactionRequest(payload);
        } else if ("LEDGER_REVERSAL_REQUEST".equals(eventTypeStr)) {
            handleLedgerReversalRequest(payload);
        }
        // Handle ORDER_PAID or other events if needed
    }

    private final org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

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

    private void handleLedgerReversalRequest(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        UUID reversalId = UUID.fromString(node.get("reversalId").asText());
        UUID originalTransactionId = UUID.fromString(node.get("orderId").asText());
        BigDecimal amount = null;
        if (node.has("amount") && !node.get("amount").isNull()) {
            amount = new BigDecimal(node.get("amount").asText());
        }
        com.fooddelivery.common.enums.FaultType faultType = com.fooddelivery.common.enums.FaultType.UNKNOWN;
        if (node.has("faultType") && !node.get("faultType").isNull()) {
            faultType = com.fooddelivery.common.enums.FaultType.valueOf(node.get("faultType").asText());
        }
        
        ledgerService.reverseTransaction(originalTransactionId, reversalId, "Post-delivery refund reversal", amount, faultType);
        log.info("Successfully processed LEDGER_REVERSAL_REQUEST for originalTransactionId: {}, reversalId: {}", originalTransactionId, reversalId);
    }

    @java.lang.SuppressWarnings("all")
    public LedgerEventListener(final DoubleEntryLedgerService ledgerService, final ObjectMapper objectMapper, final org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate) {
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }
}
