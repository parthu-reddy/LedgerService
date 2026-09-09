package com.fooddelivery.ledger.reconciliation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fooddelivery.ledger.listener.LedgerEventListener;
import com.fooddelivery.ledger.repository.ILedgerRejectionRepository;
import com.fooddelivery.ledger.entity.LedgerRejection;
import org.mockito.ArgumentCaptor;
import java.util.HashMap;
import java.util.Map;

public class LedgerDltRejectionTest {

    @Test
    public void testDltRejection() {
        ILedgerRejectionRepository repo = mock(ILedgerRejectionRepository.class);
        LedgerEventListener listener = new LedgerEventListener(
            null, null, null, null, repo, null
        );

        Map<String, Object> headers = new HashMap<>();
        headers.put("eventId", "test-dlt-event-id");

        listener.handleDltEvent("{\"dummy\":\"payload\"}", "test-topic-dlt", headers);

        ArgumentCaptor<LedgerRejection> captor = ArgumentCaptor.forClass(LedgerRejection.class);
        verify(repo).save(captor.capture());

        LedgerRejection saved = captor.getValue();
        assertNotNull(saved);
        assertEquals("test-dlt-event-id", saved.getEventId());
        assertEquals("DLT", saved.getProducer());
        assertEquals("{\"dummy\":\"payload\"}", saved.getPayload());
        assertEquals("Failed after all retries", saved.getReason());
    }

    @Test
    public void testDltRejectionWithMissingHeaders() {
        ILedgerRejectionRepository repo = mock(ILedgerRejectionRepository.class);
        LedgerEventListener listener = new LedgerEventListener(
            null, null, null, null, repo, null
        );

        Map<String, Object> headers = new HashMap<>();

        listener.handleDltEvent("{\"dummy\":\"payload\"}", "test-topic-dlt", headers);

        ArgumentCaptor<LedgerRejection> captor = ArgumentCaptor.forClass(LedgerRejection.class);
        verify(repo).save(captor.capture());

        LedgerRejection saved = captor.getValue();
        assertNotNull(saved);
        assertEquals("UNKNOWN_DLT", saved.getEventId());
        assertEquals("DLT", saved.getProducer());
    }
}
