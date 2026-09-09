package com.fooddelivery.ledger.service;

import com.fooddelivery.ledger.client.DriverSummaryClient;
import com.fooddelivery.ledger.client.RestaurantSummaryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class OwnerNameResolverTest {

    @Mock
    private RestaurantSummaryClient restaurantClient;

    @Mock
    private DriverSummaryClient driverClient;

    @InjectMocks
    private OwnerNameResolver ownerNameResolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testResolveDisplayName_Restaurant() {
        UUID ownerId = UUID.randomUUID();
        when(restaurantClient.getOutletSummary(ownerId)).thenReturn(Map.of("name", "Test Outlet", "brandName", "Test Brand"));

        OwnerNameResolver.ResolvedName name = ownerNameResolver.resolveDisplayName("RESTAURANT", ownerId);
        assertEquals("Test Outlet (Test Brand)", name.displayName());
        assertTrue(name.resolved());
        verify(restaurantClient, times(1)).getOutletSummary(ownerId);
    }

    @Test
    void testResolveDisplayName_Driver() {
        UUID ownerId = UUID.randomUUID();
        when(driverClient.getDriverSummary(ownerId)).thenReturn(Map.of("name", "Test Driver"));

        OwnerNameResolver.ResolvedName name = ownerNameResolver.resolveDisplayName("DRIVER", ownerId);
        assertEquals("Test Driver", name.displayName());
        assertTrue(name.resolved());
        verify(driverClient, times(1)).getDriverSummary(ownerId);
    }

    @Test
    void testResolveDisplayNames_Batch() {
        UUID ownerId1 = UUID.randomUUID();
        UUID ownerId2 = UUID.randomUUID();
        
        when(restaurantClient.getOutletSummaries(any())).thenReturn(List.of(
            Map.of("id", ownerId1.toString(), "name", "Test Outlet 1"),
            Map.of("id", ownerId2.toString(), "name", "Test Outlet 2", "brandName", "Test Brand 2")
        ));

        Map<UUID, OwnerNameResolver.ResolvedName> names =
                ownerNameResolver.resolveDisplayNames("RESTAURANT", List.of(ownerId1, ownerId2));
        assertEquals(2, names.size());
        assertEquals("Test Outlet 1", names.get(ownerId1).displayName());
        assertTrue(names.get(ownerId1).resolved());
        assertEquals("Test Outlet 2 (Test Brand 2)", names.get(ownerId2).displayName());
        
        // Second call should hit cache and not invoke client again
        ownerNameResolver.resolveDisplayNames("RESTAURANT", List.of(ownerId1, ownerId2));
        verify(restaurantClient, times(1)).getOutletSummaries(any());
    }

    /**
     * A name that could not be fetched must say so. The previous resolver returned
     * "RESTAURANT <id>" indistinguishably from a real name, and PayoutService stamped that string
     * into payouts.payee_display_name -- the audit record of who was paid.
     */
    @Test
    void unresolvedNameIsFlaggedAndNotCached() {
        UUID ownerId = UUID.randomUUID();
        when(restaurantClient.getOutletSummary(ownerId))
                .thenThrow(new RuntimeException("outlet service down"))
                .thenReturn(Map.of("name", "Back Online"));

        OwnerNameResolver.ResolvedName first = ownerNameResolver.resolveDisplayName("RESTAURANT", ownerId);
        assertFalse(first.resolved(), "a name the outlet service could not supply must not be reported as resolved");
        assertEquals("RESTAURANT " + ownerId, first.displayName());

        // A failure must not poison the cache for ten minutes: the next call retries.
        OwnerNameResolver.ResolvedName second = ownerNameResolver.resolveDisplayName("RESTAURANT", ownerId);
        assertTrue(second.resolved());
        assertEquals("Back Online", second.displayName());
    }

    /** A batch response missing the name for one payee must not invent one. */
    @Test
    void batchWithoutANameLeavesThatPayeeUnresolved() {
        UUID named = UUID.randomUUID();
        UUID nameless = UUID.randomUUID();
        when(restaurantClient.getOutletSummaries(any())).thenReturn(List.of(
                Map.of("id", named.toString(), "name", "Has A Name")));

        Map<UUID, OwnerNameResolver.ResolvedName> names =
                ownerNameResolver.resolveDisplayNames("RESTAURANT", List.of(named, nameless));

        assertTrue(names.get(named).resolved());
        assertFalse(names.get(nameless).resolved());
    }
}
