package com.fooddelivery.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.ledger.entity.LedgerRejection;
import com.fooddelivery.ledger.repository.ILedgerRejectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A rejected ledger movement is money that was never booked. It has to be visible and closable.
 *
 * <p>The table had resolved_at, resolved_by and resolution_note from the first migration and no
 * endpoint could ever set them, so an unresolved rejection raised a STUCK break that nothing could
 * clear -- and after the DLT path was changed to record a rejection rather than publish to a topic
 * nobody consumed, this became the only place a lost ledger event appears at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminLedgerRejectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ILedgerRejectionRepository rejectionRepository;

    private LedgerRejection unresolved(UUID id) {
        return LedgerRejection.builder()
                .id(id)
                .eventId("evt-1")
                .producer("customer-application")
                .reason("transactionId 0000 is not derivable from (producer=customer-application, reference=..., leg=DELIVERED)")
                .payload("{\"transactionId\":\"0000\"}")
                .createdAt(OffsetDateTime.now().minusHours(3))
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listsUnresolvedRejectionsOldestFirst() throws Exception {
        UUID id = UUID.randomUUID();
        when(rejectionRepository.findByResolvedAtIsNullOrderByCreatedAtAsc(any()))
                .thenReturn(new PageImpl<>(List.of(unresolved(id))));

        mockMvc.perform(get("/api/v1/internal/admin/ledger/rejections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].producer").value("customer-application"))
                .andExpect(jsonPath("$.content[0].reason").exists())
                // The payload is what lets an operator understand and replay the movement.
                .andExpect(jsonPath("$.content[0].payload").exists())
                .andExpect(jsonPath("$.content[0].ageMinutes").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void resolvingRecordsWhoAndWhy() throws Exception {
        UUID id = UUID.randomUUID();
        LedgerRejection rejection = unresolved(id);
        when(rejectionRepository.findById(id)).thenReturn(Optional.of(rejection));
        when(rejectionRepository.save(any(LedgerRejection.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/v1/internal/admin/ledger/rejections/" + id + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "replayed by the producer on 2026-09-09")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionNote").value("replayed by the producer on 2026-09-09"))
                .andExpect(jsonPath("$.resolvedAt").exists());

        assertNotNull(rejection.getResolvedAt());
        assertNotNull(rejection.getResolvedBy());
        assertEquals("replayed by the producer on 2026-09-09", rejection.getResolutionNote());
    }

    /** The note is the audit record of why money that was refused no longer needs booking. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void resolvingWithoutANoteIsRefused() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/internal/admin/ledger/rejections/" + id + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"   \"}")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());

        verify(rejectionRepository, never()).save(any());
    }

    /** Re-resolving must not overwrite who first signed it off. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void resolvingTwiceKeepsTheFirstSignOff() throws Exception {
        UUID id = UUID.randomUUID();
        LedgerRejection already = unresolved(id);
        already.setResolvedAt(OffsetDateTime.parse("2026-09-01T10:00:00Z"));
        already.setResolvedBy("first-admin");
        already.setResolutionNote("the original note");
        when(rejectionRepository.findById(id)).thenReturn(Optional.of(already));

        mockMvc.perform(post("/api/v1/internal/admin/ledger/rejections/" + id + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"a second opinion\"}")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedBy").value("first-admin"))
                .andExpect(jsonPath("$.resolutionNote").value("the original note"));

        verify(rejectionRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void aCustomerCannotSeeRejectedMoneyMovements() throws Exception {
        mockMvc.perform(get("/api/v1/internal/admin/ledger/rejections"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void theUnresolvedCountIsTheSignalTheStuckBreakUses() throws Exception {
        when(rejectionRepository.countByResolvedAtIsNull()).thenReturn(4L);

        mockMvc.perform(get("/api/v1/internal/admin/ledger/rejections/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(4));
    }
}
