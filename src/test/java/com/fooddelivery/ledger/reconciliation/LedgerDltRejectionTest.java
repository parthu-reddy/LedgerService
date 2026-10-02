package com.fooddelivery.ledger.reconciliation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fooddelivery.ledger.listener.LedgerEventListener;
import com.fooddelivery.ledger.service.LedgerRejectionRecorder;
import org.mockito.ArgumentCaptor;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LedgerDltRejectionTest {

    @Test
    public void testDltRejection() {
        LedgerRejectionRecorder recorder = mock(LedgerRejectionRecorder.class);
        ObjectMapper mapper = new ObjectMapper();
        // handleDltEvent touches only the rejection recorder; the rest stay null
        LedgerEventListener listener = new LedgerEventListener(
            null, mapper, null, null, recorder, null, null
        );

        Map<String, Object> headers = new HashMap<>();
        headers.put("eventId", "test-dlt-event-id");

        listener.handleDltEvent("{\"dummy\":\"payload\"}", "test-topic-dlt", headers);

        verify(recorder).record(
            eq("dlt_event:test-dlt-event-id"),
            eq("test-dlt-event-id"),
            eq("DLT"),
            eq("{\"dummy\":\"payload\"}"),
            eq("Failed after all retries")
        );
    }

    @Test
    public void testDltRejectionWithMissingHeaders() {
        LedgerRejectionRecorder recorder = mock(LedgerRejectionRecorder.class);
        ObjectMapper mapper = new ObjectMapper();
        LedgerEventListener listener = new LedgerEventListener(
            null, mapper, null, null, recorder, null, null
        );

        Map<String, Object> headers = new HashMap<>();

        listener.handleDltEvent("{\"dummy\":\"payload\"}", "test-topic-dlt", headers);

        verify(recorder).record(
            anyString(), // processedEventKey
            anyString(), // eventId
            eq("DLT"),
            eq("{\"dummy\":\"payload\"}"),
            eq("Failed after all retries")
        );
    }

    /**
     * An exhausted ledger event is recorded for the reconciliation queue and published nowhere.
     *
     * <p>It used to be forwarded verbatim to `ledger-events-dlq`, where WalletService's
     * `LedgerFailureConsumer` credited an advertiser back automatically. Both halves of that route
     * were deleted -- the consumer on 2026-09-05, the producer's `kafkaTemplate.send` on
     * 2026-09-09 -- and replaced by this operator-resolved queue, which `ReconciliationService`
     * alarms on when rows go unresolved.
     *
     * <p>The contract describing the removed route was left behind, and its generated
     * `MessagingTest` had been red ever since: it waited five seconds for a message nothing sends.
     * If a send ever comes back it needs a consumer and a contract again, and this assertion is
     * where that argument should start.
     */
    @Test
    public void testNothingIsPublishedForADltEvent() {
        boolean holdsAProducer = java.util.Arrays.stream(LedgerEventListener.class.getDeclaredFields())
                .anyMatch(f -> f.getType().getName().contains("KafkaTemplate"));
        assertFalse(holdsAProducer,
                "LedgerEventListener kept a KafkaTemplate it never used after the DLQ forward was "
                        + "removed; the reconciliation queue is the whole compensation route now");
    }
}
