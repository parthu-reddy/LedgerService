package com.fooddelivery.ledger.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.enums.AccountType;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.util.KafkaHeaderUtils;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final DoubleEntryLedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "5",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false"
    )
    @KafkaListener(topics = KafkaConstants.TOPIC_ORDER_EVENTS, groupId = KafkaConstants.GROUP_LEDGER_SERVICE)
    public void handleEvents(String payload, @Headers Map<String, Object> headers) throws Exception {
        JsonNode rootNode = objectMapper.readTree(payload);
        String eventTypeStr = KafkaHeaderUtils.extractEventType(headers, rootNode);
        
        if (EventType.LEDGER_TRANSACTION_REQUEST.name().equals(eventTypeStr)) {
            handleLedgerTransactionRequest(payload);
        }
        // Handle ORDER_PAID or other events if needed
    }

    @DltHandler
    public void handleDltEvent(String payload, 
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, 
                               @Headers Map<String, Object> headers) {
        log.error("Event failed after all retries. Payload: {}, Topic: {}, Headers: {}", payload, topic, headers);
        // Here we could persist it to a dead letter table in the database if needed
    }

    private void handleLedgerTransactionRequest(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        UUID transferId = UUID.fromString(node.get("transferId").asText());
        UUID fromId = UUID.fromString(node.get("fromId").asText());
        AccountType fromType = AccountType.valueOf(node.get("fromType").asText());
        UUID toId = UUID.fromString(node.get("toId").asText());
        AccountType toType = AccountType.valueOf(node.get("toType").asText());
        BigDecimal amount = new BigDecimal(node.get("amount").asText());
        ChargeCategory category = null;
        if (node.has("chargeCategory")) {
            category = ChargeCategory.valueOf(node.get("chargeCategory").asText());
        }
        
        ledgerService.recordTransaction(transferId, fromId, fromType, toId, toType, amount, category);
        log.info("Successfully processed LEDGER_TRANSACTION_REQUEST for transferId: {}", transferId);
    }
}
