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

    @BeforeEach
    void setUp() {
        ledgerService = mock(DoubleEntryLedgerService.class);
        objectMapper = new ObjectMapper();
        idempotencyKeyRepository = mock(IIdempotencyKeyRepository.class);
        rejectionRepository = mock(ILedgerRejectionRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        listener = new LedgerEventListener(ledgerService, objectMapper, idempotencyKeyRepository, rejectionRepository, transactionTemplate);
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
        verify(rejectionRepository, times(1)).save(any());
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
}
