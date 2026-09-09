package com.fooddelivery.ledger.service;

import com.fooddelivery.common.dto.ledger.CashSummaryDto;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.ledger.dto.CashRemittanceRequest;
import com.fooddelivery.ledger.entity.CashRemittance;
import com.fooddelivery.ledger.repository.CashRemittanceRepository;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashService {

    private final CashRemittanceRepository cashRemittanceRepository;
    private final DoubleEntryLedgerService doubleEntryLedgerService;
    private final ILedgerAccountRepository accountRepository;
    private final ILedgerEntryRepository entryRepository;

    @Transactional
    public CashRemittance recordCashRemittance(CashRemittanceRequest request, UUID adminId) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("REMITTANCE_AMOUNT_INVALID");
        }

        BigDecimal inHand = getCashInHand(request.getDriverId());
        if (inHand.compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException("REMITTANCE_EXCEEDS_CASH_IN_HAND: driver "
                    + request.getDriverId() + " holds " + inHand + " but " + request.getAmount()
                    + " was recorded as remitted");
        }

        UUID remittanceId = UUID.randomUUID();
        UUID ledgerTransactionId = com.fooddelivery.common.util.DeterministicIdUtils.ledgerId("ledger-service", remittanceId, "REMIT");

        // BANK -> CASH_RECEIVABLE, not the other way round. Collecting cash debits CASH_RECEIVABLE
        // (the rider now owes the platform); remitting must credit it back, exactly as a gateway
        // refund credits GATEWAY_RECEIVABLE to undo a capture. Debiting it a second time booked the
        // same cash twice and left cash-in-hand growing with every remittance instead of clearing.
        LedgerLeg leg = new LedgerLeg(
                LedgerAccountType.BANK, com.fooddelivery.common.constants.LedgerAccounts.BANK,
                LedgerAccountType.CASH_RECEIVABLE, request.getDriverId(),
                request.getAmount(),
                ChargeCategory.CASH_REMITTED,
                "Cash Remittance",
                adminId.toString()
        );

        LedgerTransactionCommand txReq = new LedgerTransactionCommand(
                ledgerTransactionId, remittanceId, "ledger-service", "REMIT", List.of(leg)
        );
        doubleEntryLedgerService.record(txReq);

        CashRemittance remittance = CashRemittance.builder()
                .id(remittanceId)
                .driverId(request.getDriverId())
                .amount(request.getAmount())
                .reference(request.getReference())
                .recordedBy(adminId)
                .ledgerTransactionId(ledgerTransactionId)
                .build();

        return cashRemittanceRepository.save(remittance);
    }

    /**
     * Cash the rider is physically holding: everything collected on delivery, less everything
     * remitted. CASH_RECEIVABLE carries a net-debit balance while cash is outstanding, so the amount
     * owed is the negation of the account balance.
     */
    @Transactional(readOnly = true)
    public BigDecimal getCashInHand(UUID driverId) {
        return accountRepository.findByOwnerIdAndOwnerType(driverId, LedgerAccountType.CASH_RECEIVABLE)
                .map(a -> a.getBalance().negate())
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public CashSummaryDto getCashSummary(UUID driverId) {
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal remitted = BigDecimal.ZERO;
        var account = accountRepository.findByOwnerIdAndOwnerType(driverId, LedgerAccountType.CASH_RECEIVABLE);
        if (account.isPresent()) {
            UUID accountId = account.get().getId();
            collected = nz(entryRepository.sumByAccountAndDirectionAndCategory(
                    accountId, TransactionDirection.DEBIT, ChargeCategory.CASH_COLLECTED));
            remitted = nz(entryRepository.sumByAccountAndDirectionAndCategory(
                    accountId, TransactionDirection.CREDIT, ChargeCategory.CASH_REMITTED));
        }
        return CashSummaryDto.builder()
                .driverId(driverId)
                .cashCollected(collected)
                .cashRemitted(remitted)
                .cashInHand(collected.subtract(remitted))
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CashRemittance> getCashRemittancesByDriver(UUID driverId, org.springframework.data.domain.Pageable pageable) {
        return cashRemittanceRepository.findByDriverId(driverId, pageable);
    }
}
