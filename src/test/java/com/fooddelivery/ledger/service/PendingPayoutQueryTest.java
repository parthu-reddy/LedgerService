package com.fooddelivery.ledger.service;

import com.fooddelivery.common.dto.ledger.BeneficiaryResponse;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.ledger.client.BeneficiaryClient;
import com.fooddelivery.ledger.dto.PendingPayoutResponse;
import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.entity.PayoutStatus;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import com.fooddelivery.ledger.repository.PayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class PendingPayoutQueryTest {

    @Mock
    private ILedgerAccountRepository accountRepository;

    @Mock
    private PayoutRepository payoutRepository;

    @Mock
    private ILedgerEntryRepository entryRepository;

    @Mock
    private BeneficiaryClient beneficiaryClient;

    @Mock
    private OwnerNameResolver ownerNameResolver;

    @InjectMocks
    private PayoutService payoutService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testPendingPayouts() {
        UUID restaurantId = UUID.randomUUID();
        LedgerAccount account = LedgerAccount.builder()
                .ownerType(LedgerAccountType.RESTAURANT_PAYABLE)
                .ownerId(restaurantId)
                .balance(new BigDecimal("500.00"))
                .build();

        when(accountRepository.findByOwnerTypeInAndBalanceGreaterThan(any(), any())).thenReturn(List.of(account));
        when(ownerNameResolver.resolveDisplayNames(eq("RESTAURANT"), any()))
                .thenReturn(Map.of(restaurantId, new OwnerNameResolver.ResolvedName("Test Restaurant", true)));
        when(ownerNameResolver.resolveDisplayNames(eq("DRIVER"), any())).thenReturn(Map.of());
        
        Payout pendingPayout = Payout.builder().amount(new BigDecimal("100.00")).status(PayoutStatus.DRAFT).build();
        when(payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(eq("RESTAURANT"), eq(restaurantId), any()))
                .thenReturn(List.of(pendingPayout));

        when(entryRepository.findUnsettledEntries(any(), any())).thenReturn(List.of());
        when(beneficiaryClient.getBeneficiary(eq("RESTAURANT"), eq(restaurantId))).thenReturn(
                BeneficiaryResponse.builder().verified(true).source("STRIPE").build());

        List<PendingPayoutResponse> responses = payoutService.pending();

        assertEquals(1, responses.size());
        assertEquals("Test Restaurant", responses.get(0).getDisplayName());
        assertTrue(responses.get(0).isNameResolved());
        assertEquals(new BigDecimal("400.00"), responses.get(0).getUnsettledAmount());
        assertEquals(true, responses.get(0).getBeneficiaryStatus().isVerified());
    }

    /**
     * A payee whose owning service cannot name them is shown as unresolved, never as a plausible
     * name built from their id. The queue used to print "RESTAURANT 1a2b3c4d" with no way for the
     * screen to tell that apart from a real outlet name.
     */
    @Test
    void unresolvableNameIsFlaggedOnTheQueueRow() {
        UUID restaurantId = UUID.randomUUID();
        LedgerAccount account = LedgerAccount.builder()
                .ownerType(LedgerAccountType.RESTAURANT_PAYABLE)
                .ownerId(restaurantId)
                .balance(new BigDecimal("500.00"))
                .build();

        when(accountRepository.findByOwnerTypeInAndBalanceGreaterThan(any(), any())).thenReturn(List.of(account));
        when(ownerNameResolver.resolveDisplayNames(eq("RESTAURANT"), any())).thenReturn(
                Map.of(restaurantId, OwnerNameResolver.ResolvedName.unresolved("RESTAURANT", restaurantId)));
        when(ownerNameResolver.resolveDisplayNames(eq("DRIVER"), any())).thenReturn(Map.of());
        when(payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(eq("RESTAURANT"), eq(restaurantId), any()))
                .thenReturn(List.of());
        when(entryRepository.findUnsettledEntries(any(), any())).thenReturn(List.of());
        when(beneficiaryClient.getBeneficiary(eq("RESTAURANT"), eq(restaurantId))).thenReturn(
                BeneficiaryResponse.builder().verified(true).source("STRIPE").build());

        List<PendingPayoutResponse> responses = payoutService.pending();

        assertEquals(1, responses.size());
        assertFalse(responses.get(0).isNameResolved(),
                "an unresolved payee must be flagged so the screen can say so");
    }

    @Test
    void testPendingPayoutsEmpty() {
        when(accountRepository.findByOwnerTypeInAndBalanceGreaterThan(any(), any())).thenReturn(List.of());
        
        List<PendingPayoutResponse> responses = payoutService.pending();
        
        assertEquals(0, responses.size());
    }
}
