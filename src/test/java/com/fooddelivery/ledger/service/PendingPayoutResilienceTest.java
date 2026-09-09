package com.fooddelivery.ledger.service;

import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.ledger.client.BeneficiaryClient;
import com.fooddelivery.ledger.dto.PendingPayoutResponse;
import com.fooddelivery.ledger.entity.LedgerAccount;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class PendingPayoutResilienceTest {

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
    void testPendingPayoutResilience_WhenBeneficiaryClientFails() {
        UUID accountId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        
        LedgerAccount account = new LedgerAccount();
        account.setId(accountId);
        account.setOwnerId(ownerId);
        account.setOwnerType(LedgerAccountType.RESTAURANT_PAYABLE);
        account.setBalance(new BigDecimal("100.00"));

        when(accountRepository.findByOwnerTypeInAndBalanceGreaterThan(any(), any()))
                .thenReturn(List.of(account));
        
        when(payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(anyString(), any(), any()))
                .thenReturn(List.of());
                
        when(entryRepository.findUnsettledEntries(any(), any()))
                .thenReturn(List.of());

        when(beneficiaryClient.getBeneficiary(anyString(), any()))
                .thenThrow(new RuntimeException("Service Unavailable"));

        List<PendingPayoutResponse> responses = payoutService.pending();

        assertEquals(1, responses.size());
        assertEquals(new BigDecimal("100.00"), responses.get(0).getUnsettledAmount());
        assertEquals("UNAVAILABLE", responses.get(0).getBeneficiaryStatus().getSource());
    }

    /** One unreachable payee must not blank the whole queue. */
    @Test
    void oneFailingPayeeDoesNotTakeDownTheOthers() {
        UUID reachable = UUID.randomUUID();
        UUID unreachable = UUID.randomUUID();

        when(accountRepository.findByOwnerTypeInAndBalanceGreaterThan(any(), any()))
                .thenReturn(List.of(account(reachable, "100.00"), account(unreachable, "250.00")));
        when(payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(anyString(), any(), any())).thenReturn(List.of());
        when(entryRepository.findUnsettledEntries(any(), any())).thenReturn(List.of());
        when(ownerNameResolver.resolveDisplayNames(anyString(), any())).thenReturn(java.util.Map.of());
        when(beneficiaryClient.getBeneficiary(anyString(), eq(reachable)))
                .thenReturn(com.fooddelivery.common.dto.ledger.BeneficiaryResponse.builder()
                        .verified(true).source("RAZORPAY").build());
        when(beneficiaryClient.getBeneficiary(anyString(), eq(unreachable)))
                .thenThrow(new RuntimeException("Service Unavailable"));

        List<PendingPayoutResponse> responses = payoutService.pending();

        assertEquals(2, responses.size(), "a failing payee must not remove the others from the queue");
        // Sorted by amount, so the unreachable 250.00 payee is first.
        assertEquals("UNAVAILABLE", responses.get(0).getBeneficiaryStatus().getSource());
        assertEquals("RAZORPAY", responses.get(1).getBeneficiaryStatus().getSource());
    }

    /** A payee whose name could not be fetched is flagged, not given a plausible-looking one. */
    @Test
    void anUnresolvedPayeeNameIsFlagged() {
        UUID ownerId = UUID.randomUUID();
        when(accountRepository.findByOwnerTypeInAndBalanceGreaterThan(any(), any()))
                .thenReturn(List.of(account(ownerId, "100.00")));
        when(payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(anyString(), any(), any())).thenReturn(List.of());
        when(entryRepository.findUnsettledEntries(any(), any())).thenReturn(List.of());
        when(ownerNameResolver.resolveDisplayNames(anyString(), any())).thenReturn(java.util.Map.of());
        when(beneficiaryClient.getBeneficiary(anyString(), any()))
                .thenReturn(com.fooddelivery.common.dto.ledger.BeneficiaryResponse.builder().verified(true).build());

        List<PendingPayoutResponse> responses = payoutService.pending();

        assertFalse(responses.get(0).isNameResolved());
    }

    private LedgerAccount account(UUID ownerId, String balance) {
        LedgerAccount account = new LedgerAccount();
        account.setId(UUID.randomUUID());
        account.setOwnerId(ownerId);
        account.setOwnerType(LedgerAccountType.RESTAURANT_PAYABLE);
        account.setBalance(new BigDecimal(balance));
        return account;
    }
}
