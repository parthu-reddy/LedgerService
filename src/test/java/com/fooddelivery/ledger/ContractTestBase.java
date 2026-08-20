package com.fooddelivery.ledger;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import com.fooddelivery.ledger.controller.LedgerController;

public abstract class ContractTestBase {

    @BeforeEach
    public void setup() {

        ILedgerAccountRepository accountRepository = Mockito.mock(ILedgerAccountRepository.class);
        DoubleEntryLedgerService ledgerService = Mockito.mock(DoubleEntryLedgerService.class);
        Mockito.when(accountRepository.findByOwnerTypeInAndBalanceGreaterThan(Mockito.anyList(), Mockito.any()))
               .thenReturn(java.util.Collections.emptyList());
        // getOrderLedgerAmount contract: the ledger's total for one order's reference UUID.
        Mockito.when(ledgerService.getOrderLedgerTotal(Mockito.any(java.util.UUID.class)))
               .thenReturn(new java.math.BigDecimal("100.50"));
        LedgerController controller = new LedgerController(accountRepository, ledgerService);
        RestAssuredMockMvc.standaloneSetup(controller);
        
    }
}
