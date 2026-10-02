package com.fooddelivery.ledger.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.ledger.listener.LedgerEventListener;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import com.fooddelivery.ledger.service.LedgerRejectionRecorder;
import com.fooddelivery.ledger.repository.ILedgerRejectionRepository;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.exception.LedgerRejectedException;
import com.fooddelivery.common.constants.EventType;

import java.util.HashMap;
import java.util.Map;

public class LedgerEventListenerTest {

    private LedgerEventListener listener;
    private DoubleEntryLedgerService ledgerService;
    private ObjectMapper objectMapper;
    private IIdempotencyKeyRepository idempotencyKeyRepository;
    private ILedgerRejectionRepository rejectionRepository;
    private LedgerRejectionRecorder rejectionRecorder;

    @BeforeEach
    void setUp() {
        ledgerService = mock(DoubleEntryLedgerService.class);
        objectMapper = new ObjectMapper();
        idempotencyKeyRepository = mock(IIdempotencyKeyRepository.class);
        rejectionRepository = mock(ILedgerRejectionRepository.class);
        rejectionRecorder = mock(LedgerRejectionRecorder.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        listener = new LedgerEventListener(ledgerService, objectMapper, idempotencyKeyRepository, rejectionRepository,
                rejectionRecorder, transactionTemplate,
                new com.fooddelivery.common.event.EventBinder(objectMapper,
                        jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()));
    }

    @Test
    void testHandleEvents_Success() throws Exception {
        LedgerTransactionCommand cmd = new LedgerTransactionCommand();
        cmd.setTransactionId(java.util.UUID.randomUUID());
        cmd.setProducer("TEST_PRODUCER");
        
        String payload = objectMapper.writeValueAsString(cmd);
        Map<String, Object> headers = new HashMap<>();
        headers.put("eventId", "event-1");
        headers.put("eventType", EventType.LEDGER_TRANSACTION_REQUEST.name());

        when(idempotencyKeyRepository.existsById("processed_event:event-1")).thenReturn(false);

        listener.handleEvents(payload, headers);

        verify(ledgerService, times(1)).record(any(LedgerTransactionCommand.class));
        verify(rejectionRepository, never()).save(any());
        verify(idempotencyKeyRepository, times(1)).save(any());
    }

    @Test
    void testHandleEvents_Rejection() throws Exception {
        LedgerTransactionCommand cmd = new LedgerTransactionCommand();
        cmd.setTransactionId(java.util.UUID.randomUUID());
        cmd.setProducer("TEST_PRODUCER");
        
        String payload = objectMapper.writeValueAsString(cmd);
        Map<String, Object> headers = new HashMap<>();
        headers.put("eventId", "event-2");
        headers.put("eventType", EventType.LEDGER_TRANSACTION_REQUEST.name());

        when(idempotencyKeyRepository.existsById("processed_event:event-2")).thenReturn(false);
        doThrow(new LedgerRejectedException("Invalid account")).when(ledgerService).record(any(LedgerTransactionCommand.class));

        listener.handleEvents(payload, headers);

        verify(ledgerService, times(1)).record(any(LedgerTransactionCommand.class));
        verify(rejectionRepository, never()).save(any());
        verify(rejectionRecorder).record("processed_event:event-2", "event-2", "TEST_PRODUCER", payload,
                "Invalid account");
    }
    
    @Test
    void testHandleEvents_Idempotent() throws Exception {
        String payload = "{}";
        Map<String, Object> headers = new HashMap<>();
        headers.put("eventId", "event-3");

        when(idempotencyKeyRepository.existsById("processed_event:event-3")).thenReturn(true);

        listener.handleEvents(payload, headers);

        verify(ledgerService, never()).record(any());
    }

    @Test
    void testHandleEvents_InfrastructureFailureStillEscapesForKafkaRetry() throws Exception {
        LedgerTransactionCommand cmd = new LedgerTransactionCommand();
        cmd.setTransactionId(java.util.UUID.randomUUID());
        cmd.setProducer("TEST_PRODUCER");
        String payload = objectMapper.writeValueAsString(cmd);
        Map<String, Object> headers = new HashMap<>();
        headers.put("eventId", "event-infrastructure-failure");
        headers.put("eventType", EventType.LEDGER_TRANSACTION_REQUEST.name());
        when(idempotencyKeyRepository.existsById("processed_event:event-infrastructure-failure")).thenReturn(false);
        doThrow(new IllegalStateException("database unavailable"))
                .when(ledgerService).record(any(LedgerTransactionCommand.class));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> listener.handleEvents(payload, headers));

        assertEquals("database unavailable", thrown.getMessage());
        verify(rejectionRecorder, never()).record(any(), any(), any(), any(), any());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{\"id\":\"1\"}", "invalid JSON", ""})
    void deadLettersRetainJsonOrTheExactMalformedInputWithStableIdentity(String payload) throws Exception {
        var headers = Map.<String, Object>of("eventId", "dlt-owned-event",
                org.springframework.kafka.support.KafkaHeaders.EXCEPTION_MESSAGE, "x".repeat(1500));
        listener.handleDltEvent(payload, "ledger-events-dlt", headers);
        var retained = org.mockito.ArgumentCaptor.forClass(String.class);
        var reason = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(rejectionRecorder).record(eq("dlt_event:dlt-owned-event"), eq("dlt-owned-event"), eq("DLT"), retained.capture(), reason.capture());
        var json = objectMapper.readTree(retained.getValue());
        assertNotNull(json);
        if (payload.startsWith("{")) assertEquals("1", json.get("id").asText());
        else assertEquals(payload, json.get("invalidJsonPayload").asText());
        assertEquals(1000, reason.getValue().length());
        verifyNoInteractions(rejectionRepository, ledgerService, idempotencyKeyRepository);
    }

}
