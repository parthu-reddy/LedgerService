package com.fooddelivery.ledger.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fooddelivery.ledger.client.DriverSummaryClient;
import com.fooddelivery.ledger.client.RestaurantSummaryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Resolves the human name behind a payee id.
 *
 * <p>A name that could not be resolved is never dressed up as one. The previous version returned
 * {@code "<TYPE> <id>"} on failure, cached that string for ten minutes, and {@code PayoutService}
 * stamped it into {@code payouts.payee_display_name} -- the audit record of who was paid. Callers
 * now get a {@link ResolvedName} that says whether the name is real, and unresolved names are not
 * cached, so one unreachable service does not blind the payout queue for ten minutes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerNameResolver {

    /** A display name and whether it came from the owning service or is a stand-in for the id. */
    public record ResolvedName(String displayName, boolean resolved) {
        public static ResolvedName unresolved(String ownerType, UUID ownerId) {
            return new ResolvedName(ownerType + " " + ownerId, false);
        }
    }

    private final RestaurantSummaryClient restaurantClient;
    private final DriverSummaryClient driverClient;

    private final Cache<String, String> nameCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    public ResolvedName resolveDisplayName(String ownerType, UUID ownerId) {
        String cacheKey = ownerType + ":" + ownerId;
        String cached = nameCache.getIfPresent(cacheKey);
        if (cached != null) {
            return new ResolvedName(cached, true);
        }
        String fetched = fetchName(ownerType, ownerId);
        if (fetched == null) {
            return ResolvedName.unresolved(ownerType, ownerId);
        }
        nameCache.put(cacheKey, fetched);
        return new ResolvedName(fetched, true);
    }

    /** @return the real name, or {@code null} when the owning service could not supply one. */
    private String fetchName(String ownerType, UUID ownerId) {
        try {
            if ("RESTAURANT".equals(ownerType)) {
                return outletName(restaurantClient.getOutletSummary(ownerId));
            } else if ("DRIVER".equals(ownerType)) {
                Map<String, String> summary = driverClient.getDriverSummary(ownerId);
                return summary != null ? blankToNull(summary.get("name")) : null;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch display name for {} {}: {}", ownerType, ownerId, e.getMessage());
        }
        return null;
    }

    private static String outletName(Map<String, String> summary) {
        if (summary == null) return null;
        String name = blankToNull(summary.get("name"));
        if (name == null) return null;
        String brand = blankToNull(summary.get("brandName"));
        return brand == null ? name : name + " (" + brand + ")";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public Map<UUID, ResolvedName> resolveDisplayNames(String ownerType, List<UUID> ownerIds) {
        List<UUID> missingIds = ownerIds.stream()
                .distinct()
                .filter(id -> nameCache.getIfPresent(ownerType + ":" + id) == null)
                .collect(Collectors.toList());

        if (!missingIds.isEmpty()) {
            try {
                List<Map<String, String>> summaries = "RESTAURANT".equals(ownerType)
                        ? restaurantClient.getOutletSummaries(missingIds)
                        : driverClient.getDriverSummaries(missingIds);
                if (summaries != null) {
                    for (Map<String, String> s : summaries) {
                        String id = s.get("id");
                        String name = "RESTAURANT".equals(ownerType) ? outletName(s) : blankToNull(s.get("name"));
                        if (id != null && name != null) {
                            nameCache.put(ownerType + ":" + id, name);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to batch fetch display names for {}: {}", ownerType, e.getMessage());
            }
        }

        Map<UUID, ResolvedName> out = new HashMap<>();
        for (UUID id : ownerIds) {
            String cached = nameCache.getIfPresent(ownerType + ":" + id);
            out.put(id, cached != null
                    ? new ResolvedName(cached, true)
                    : ResolvedName.unresolved(ownerType, id));
        }
        return out;
    }
}
