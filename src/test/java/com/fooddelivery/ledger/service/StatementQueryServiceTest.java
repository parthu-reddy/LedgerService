package com.fooddelivery.ledger.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fooddelivery.ledger.service.StatementQueryService;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;
import com.fooddelivery.ledger.entity.LedgerAccount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

public class StatementQueryServiceTest {

    private StatementQueryService service;
    private EntityManager entityManager;
    private ILedgerAccountRepository accountRepo;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        accountRepo = mock(ILedgerAccountRepository.class);
        service = new StatementQueryService(entityManager, accountRepo);
    }

    @Test
    void testGetStatement_EmptyWhenAccountNotFound() {
        UUID ownerId = UUID.randomUUID();
        when(accountRepo.findByOwnerIdAndOwnerType(ownerId, LedgerAccountType.RESTAURANT_PAYABLE)).thenReturn(Optional.empty());

        Page<LedgerStatementLineDto> result = service.getStatement(LedgerAccountType.RESTAURANT_PAYABLE, ownerId, null, null, null, PageRequest.of(0, 10));
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testGetStatement_ReturnsResults() {
        UUID ownerId = UUID.randomUUID();
        LedgerAccount mockAccount = mock(LedgerAccount.class);
        when(mockAccount.getId()).thenReturn(UUID.randomUUID());
        when(accountRepo.findByOwnerIdAndOwnerType(ownerId, LedgerAccountType.RESTAURANT_PAYABLE)).thenReturn(Optional.of(mockAccount));

        Query countQuery = mock(Query.class);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        Query dataQuery = mock(Query.class);
        when(dataQuery.setParameter(anyString(), any())).thenReturn(dataQuery);
        when(dataQuery.setFirstResult(anyInt())).thenReturn(dataQuery);
        when(dataQuery.setMaxResults(anyInt())).thenReturn(dataQuery);
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[] {
            UUID.randomUUID(), UUID.randomUUID(), "ORDER_TOTAL", new java.math.BigDecimal("150.00"), "CREDIT", 
            new java.sql.Timestamp(System.currentTimeMillis()), "Test description", null, null, mockAccount.getId(),
            ownerId, "RESTAURANT_PAYABLE"
        });
        when(dataQuery.getResultList()).thenReturn(mockResults);

        when(entityManager.createNativeQuery(contains("COUNT(*)"))).thenReturn(countQuery);
        when(entityManager.createNativeQuery(contains("ORDER BY e.created_at DESC"))).thenReturn(dataQuery);

        Page<LedgerStatementLineDto> result = service.getStatement(LedgerAccountType.RESTAURANT_PAYABLE, ownerId, null, null, null, PageRequest.of(0, 10));
        assertFalse(result.isEmpty());
        assertEquals(1, result.getTotalElements());
        assertEquals("ORDER_TOTAL", result.getContent().get(0).getCategory().name());
        // The party the line belongs to, not the account's surrogate key: this is what callers
        // attribute a line by, and matching on accountId instead is why the clawback cap never applied.
        assertEquals(ownerId, result.getContent().get(0).getOwnerId());
        assertEquals(LedgerAccountType.RESTAURANT_PAYABLE, result.getContent().get(0).getOwnerType());
    }
    
    @Test
    void testGetStatementByReferenceId_ReturnsResults() {
        UUID refId = UUID.randomUUID();
        Query query = mock(Query.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[] {
            UUID.randomUUID(), refId, "ORDER_TOTAL", new java.math.BigDecimal("150.00"), "CREDIT", 
            new java.sql.Timestamp(System.currentTimeMillis()), "Test description", null, null, UUID.randomUUID(),
            UUID.randomUUID(), "RESTAURANT_PAYABLE"
        });
        
        when(query.getResultList()).thenReturn(mockResults);
        
        List<LedgerStatementLineDto> dtos = service.getStatementByReferenceId(refId);
        assertEquals(1, dtos.size());
        assertEquals(refId, dtos.get(0).getReferenceId());
        assertEquals("ORDER_TOTAL", dtos.get(0).getCategory().name());
    }
}
