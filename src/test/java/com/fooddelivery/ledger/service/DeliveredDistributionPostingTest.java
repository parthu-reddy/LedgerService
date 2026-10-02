package com.fooddelivery.ledger.service;

import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.common.util.DeterministicIdUtils;
import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.entity.LedgerEntry;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The delivered-order distribution exactly as LedgerBookkeeper sent it for order 7f7af6a5 on
 * 2026-10-02. Production rejected it on its second leg: UNIQUE(transaction_id, account_id,
 * direction) admitted one debit per account and this transaction debits platform clearing four
 * times, so no delivered order ever posted restaurant or rider earnings. Each leg now writes its
 * debit and credit under its own leg index; LedgerEntryLegUniquenessTest pins the constraint.
 */
class DeliveredDistributionPostingTest {

    static final UUID ORDER = UUID.fromString("7f7af6a5-1d86-4f29-b4cc-36eefe69a74b");
    static final UUID PLATFORM = new UUID(0, 0);
    static final UUID RIDER = UUID.fromString("5a41832f-5596-4bb1-a9bf-6ce416b38b95");
    static final UUID OUTLET = UUID.fromString("20000000-0000-0000-0000-000000000003");

    private final ILedgerAccountRepository accounts = mock(ILedgerAccountRepository.class);
    private final ILedgerEntryRepository entries = mock(ILedgerEntryRepository.class);
    private final DoubleEntryLedgerService service = new DoubleEntryLedgerService(accounts, entries);
    private final Map<LedgerAccountType, LedgerAccount> byType = new EnumMap<>(LedgerAccountType.class);

    static LedgerLeg leg(LedgerAccountType from, UUID fromId, LedgerAccountType to, UUID toId,
                         ChargeCategory category, String amount) {
        return new LedgerLeg(from, fromId, to, toId, new BigDecimal(amount), category, null, null);
    }

    static LedgerTransactionCommand delivered() {
        List<LedgerLeg> legs = List.of(
                leg(LedgerAccountType.PLATFORM_CLEARING, PLATFORM, LedgerAccountType.DRIVER_PAYABLE, RIDER, ChargeCategory.DELIVERY_FEE, "20.44"),
                leg(LedgerAccountType.PLATFORM_CLEARING, PLATFORM, LedgerAccountType.TAX_PAYABLE, PLATFORM, ChargeCategory.SGST, "0.43"),
                leg(LedgerAccountType.PLATFORM_CLEARING, PLATFORM, LedgerAccountType.RESTAURANT_PAYABLE, OUTLET, ChargeCategory.FOOD_COST, "17.05"),
                leg(LedgerAccountType.PLATFORM_CLEARING, PLATFORM, LedgerAccountType.TAX_PAYABLE, PLATFORM, ChargeCategory.CGST, "0.43"),
                leg(LedgerAccountType.RESTAURANT_PAYABLE, OUTLET, LedgerAccountType.DRIVER_PAYABLE, RIDER, ChargeCategory.DELIVERY_FEE, "2.56"),
                leg(LedgerAccountType.RESTAURANT_PAYABLE, OUTLET, LedgerAccountType.PLATFORM_CLEARING, PLATFORM, ChargeCategory.PLATFORM_FIXED_FEE, "5.00"),
                leg(LedgerAccountType.DRIVER_PAYABLE, RIDER, LedgerAccountType.TAX_PAYABLE, PLATFORM, ChargeCategory.SGST, "2.07"),
                leg(LedgerAccountType.DRIVER_PAYABLE, RIDER, LedgerAccountType.TAX_PAYABLE, PLATFORM, ChargeCategory.CGST, "2.07"));
        UUID tx = DeterministicIdUtils.ledgerId("customer-application", ORDER, "DELIVERED");
        return new LedgerTransactionCommand(tx, ORDER, "customer-application", "DELIVERED", legs);
    }

    @BeforeEach
    void accountsExist() {
        Map<LedgerAccountType, UUID> owners = Map.of(LedgerAccountType.PLATFORM_CLEARING, PLATFORM,
                LedgerAccountType.TAX_PAYABLE, PLATFORM, LedgerAccountType.DRIVER_PAYABLE, RIDER,
                LedgerAccountType.RESTAURANT_PAYABLE, OUTLET);
        owners.forEach((type, owner) -> {
            LedgerAccount account = LedgerAccount.builder().id(UUID.randomUUID()).ownerId(owner).ownerType(type)
                    .kind(type.getKind()).balance(BigDecimal.ZERO).currency("INR").build();
            byType.put(type, account);
            when(accounts.findByOwnerIdAndOwnerType(owner, type)).thenReturn(Optional.of(account));
            when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        });
    }

    private List<LedgerEntry> posted() {
        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entries, atLeast(0)).save(saved.capture());
        return saved.getAllValues();
    }

    @Test
    void everyLegPostsItsOwnDebitAndCreditAndThePayeesNetCorrectly() {
        service.record(delivered());

        List<LedgerEntry> posted = posted();
        assertThat(posted).hasSize(16);
        assertThat(posted.stream().map(e -> e.getLegIndex() + ":" + e.getDirection()).collect(Collectors.toSet()))
                .as("one debit and one credit per leg, as UNIQUE(transaction_id, leg_index, direction) requires")
                .hasSize(16);
        assertThat(posted).allSatisfy(e -> assertThat(e.getLegIndex()).isBetween(0, 7));
        BigDecimal debits = posted.stream().filter(e -> e.getDirection() == TransactionDirection.DEBIT)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = posted.stream().filter(e -> e.getDirection() == TransactionDirection.CREDIT)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debits).isEqualByComparingTo(credits).isEqualByComparingTo("50.05");
        // Restaurant: 17.05 food cost less 2.56 delivery contribution and 5.00 platform fee.
        assertThat(byType.get(LedgerAccountType.RESTAURANT_PAYABLE).getBalance()).isEqualByComparingTo("9.49");
        // Rider: 20.44 + 2.56 delivery fee less 4.14 tax.
        assertThat(byType.get(LedgerAccountType.DRIVER_PAYABLE).getBalance()).isEqualByComparingTo("18.86");
    }

    @Test
    void anAlreadyRecordedTransactionPostsNothing() {
        when(entries.existsByTransactionId(delivered().getTransactionId())).thenReturn(true);

        service.record(delivered());

        verify(entries, never()).save(any());
    }
}
