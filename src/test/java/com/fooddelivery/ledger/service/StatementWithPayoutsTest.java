package com.fooddelivery.ledger.service;

import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StatementWithPayoutsTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ILedgerAccountRepository accountRepository;

    @InjectMocks
    private StatementQueryService statementQueryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetStatementAccountNotFound() {
        when(accountRepository.findByOwnerIdAndOwnerType(any(), any())).thenReturn(Optional.empty());

        Page<LedgerStatementLineDto> result = statementQueryService.getStatement(
                LedgerAccountType.RESTAURANT_PAYABLE, UUID.randomUUID(), null, null, null, PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void testGetStatementWithData() {
        UUID ownerId = UUID.randomUUID();
        LedgerAccount account = new LedgerAccount();
        account.setId(UUID.randomUUID());
        when(accountRepository.findByOwnerIdAndOwnerType(any(), any())).thenReturn(Optional.of(account));

        Query countQuery = mock(Query.class);
        Query dataQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(countQuery)
                .thenReturn(dataQuery);

        when(countQuery.getSingleResult()).thenReturn(1L);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        
        when(dataQuery.setParameter(anyString(), any())).thenReturn(dataQuery);
        when(dataQuery.setFirstResult(0)).thenReturn(dataQuery);
        when(dataQuery.setMaxResults(10)).thenReturn(dataQuery);

        Object[] row = new Object[]{
                UUID.randomUUID(), UUID.randomUUID(), "ORDER_TOTAL", new BigDecimal("10.00"), "CREDIT",
                Timestamp.from(Instant.now()), "Test order", UUID.randomUUID(), "PAID", UUID.randomUUID(),
                ownerId, "RESTAURANT_PAYABLE"
        };
        when(dataQuery.getResultList()).thenReturn(java.util.Collections.singletonList(row));

        Page<LedgerStatementLineDto> result = statementQueryService.getStatement(
                LedgerAccountType.RESTAURANT_PAYABLE, ownerId, null, null, true, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("ORDER_TOTAL", result.getContent().get(0).getCategory().name());
        assertEquals(true, result.getContent().get(0).isSettled());
    }
}
