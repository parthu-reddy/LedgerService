package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.entity.PayoutLine;
import com.fooddelivery.ledger.entity.PayoutStatus;
import com.fooddelivery.ledger.repository.PayoutLineRepository;
import com.fooddelivery.ledger.repository.PayoutRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PayoutDetailTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PayoutRepository payoutRepository;

    @MockBean
    private PayoutLineRepository payoutLineRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPayoutDetail_ReturnsLines() throws Exception {
        UUID payoutId = UUID.randomUUID();
        Payout payout = Payout.builder()
                .id(payoutId)
                .payeeType("RESTAURANT")
                .payeeId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .status(PayoutStatus.APPROVED)
                .build();

        PayoutLine line = PayoutLine.builder()
                .id(UUID.randomUUID())
                .payoutId(payoutId)
                .ledgerEntryId(UUID.randomUUID())
                .referenceId(UUID.randomUUID())
                .category(ChargeCategory.ORDER_TOTAL)
                .direction(TransactionDirection.CREDIT)
                .amount(new BigDecimal("100.00"))
                .entryCreatedAt(OffsetDateTime.now())
                .active(true)
                .build();

        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));
        when(payoutLineRepository.findByPayoutId(payoutId)).thenReturn(List.of(line));

        mockMvc.perform(get("/api/v1/internal/admin/payouts/" + payoutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payoutId.toString()))
                .andExpect(jsonPath("$.lines").isArray())
                .andExpect(jsonPath("$.lines[0].id").value(line.getId().toString()))
                .andExpect(jsonPath("$.lines[0].category").value("ORDER_TOTAL"));
    }

    /**
     * The frozen contract from Phase 3 §5. The screen renders the timeline, the bank reference, the
     * failure reason and the beneficiary; the response used to carry only id, payee, amount, status
     * and lines, so every one of those came back undefined and the detail page rendered almost
     * nothing.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void getPayoutDetail_ReturnsTheWholeContract() throws Exception {
        UUID payoutId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        UUID paidBy = UUID.randomUUID();
        Payout payout = Payout.builder()
                .id(payoutId)
                .payeeType("RESTAURANT")
                .payeeId(UUID.randomUUID())
                .payeeDisplayName("Kanti Sweets")
                .periodFrom(OffsetDateTime.parse("2026-08-01T00:00:00Z"))
                .periodTo(OffsetDateTime.parse("2026-09-01T00:00:00Z"))
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .status(PayoutStatus.PAID)
                .beneficiarySnapshot("{\"accountNumberMasked\":\"XXXX1234\",\"ifsc\":\"HDFC0001\",\"verified\":true}")
                .bankReference("UTR-42")
                .createdBy(createdBy)
                .paidBy(paidBy)
                .createdAt(OffsetDateTime.parse("2026-09-01T10:00:00Z"))
                .paidAt(OffsetDateTime.parse("2026-09-02T10:00:00Z"))
                .build();

        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));
        when(payoutLineRepository.findByPayoutId(payoutId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/internal/admin/payouts/" + payoutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payeeDisplayName").value("Kanti Sweets"))
                .andExpect(jsonPath("$.bankReference").value("UTR-42"))
                .andExpect(jsonPath("$.createdBy").value(createdBy.toString()))
                .andExpect(jsonPath("$.paidBy").value(paidBy.toString()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.periodFrom").exists())
                .andExpect(jsonPath("$.beneficiary.accountNumberMasked").value("XXXX1234"))
                .andExpect(jsonPath("$.beneficiary.verified").value(true));
    }

    /** The list the drawer's "previous payouts" tab and the history screen both call. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void getPayouts_ListsAPayeesHistory() throws Exception {
        UUID payeeId = UUID.randomUUID();
        Payout payout = Payout.builder()
                .id(UUID.randomUUID()).payeeType("RESTAURANT").payeeId(payeeId)
                .amount(new BigDecimal("100.00")).status(PayoutStatus.PAID).build();
        when(payoutRepository.findByPayeeTypeAndPayeeId(org.mockito.ArgumentMatchers.eq("RESTAURANT"),
                org.mockito.ArgumentMatchers.eq(payeeId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(payout)));

        mockMvc.perform(get("/api/v1/internal/admin/payouts")
                        .param("payeeType", "RESTAURANT").param("payeeId", payeeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(payout.getId().toString()));
    }
}
