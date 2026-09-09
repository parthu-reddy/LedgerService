package com.fooddelivery.ledger.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "restaurant-service", contextId = "restaurantSummaryClient")
public interface RestaurantSummaryClient {

    @GetMapping("/api/v1/internal/restaurants/outlets/{outletId}/summary")
    Map<String, String> getOutletSummary(@PathVariable("outletId") UUID outletId);

    @PostMapping("/api/v1/internal/restaurants/outlets/summaries")
    List<Map<String, String>> getOutletSummaries(@RequestBody List<UUID> outletIds);
}
