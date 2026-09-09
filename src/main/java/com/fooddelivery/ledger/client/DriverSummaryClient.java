package com.fooddelivery.ledger.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "delivery-service", contextId = "driverSummaryClient")
public interface DriverSummaryClient {

    @GetMapping("/api/v1/internal/drivers/{driverId}")
    Map<String, String> getDriverSummary(@PathVariable("driverId") UUID driverId);

    @PostMapping("/api/v1/internal/drivers/summaries")
    List<Map<String, String>> getDriverSummaries(@RequestBody List<UUID> driverIds);
}
