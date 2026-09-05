package com.fooddelivery.ledger.service;

import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.ledger.dto.CashRemittanceRequest;
import com.fooddelivery.ledger.entity.CashRemittance;
import com.fooddelivery.ledger.repository.CashRemittanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashService {

    private final CashRemittanceRepository cashRemittanceRepository;
    private final DoubleEntryLedgerService doubleEntryLedgerService;

    @Transactional
    public CashRemittance recordCashRemittance(CashRemittanceRequest request, UUID adminId) {
        UUID ledgerTransactionId = UUID.randomUUID();
        UUID remittanceId = UUID.randomUUID();

        LedgerLeg leg = new LedgerLeg(
                LedgerAccountType.CASH_RECEIVABLE, request.getDriverId(),
                LedgerAccountType.BANK, UUID.fromString("00000000-0000-0000-0000-000000000000"),
                request.getAmount(),
                ChargeCategory.CASH_REMITTED,
                "Cash Remittance",
                adminId.toString()
        );

        LedgerTransactionCommand txReq = new LedgerTransactionCommand(
                ledgerTransactionId, remittanceId, "ledger-service", List.of(leg)
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

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CashRemittance> getCashRemittancesByDriver(UUID driverId, org.springframework.data.domain.Pageable pageable) {
        return cashRemittanceRepository.findByDriverId(driverId, pageable);
    }
}
