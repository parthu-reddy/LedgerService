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
        LedgerController controller = new LedgerController(accountRepository, ledgerService);
        RestAssuredMockMvc.standaloneSetup(controller);
        
    }
}
