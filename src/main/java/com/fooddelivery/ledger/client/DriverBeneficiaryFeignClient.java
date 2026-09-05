package com.fooddelivery.ledger.client;

import com.fooddelivery.common.dto.ledger.BeneficiaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "delivery-service")
public interface DriverBeneficiaryFeignClient {
    @GetMapping("/api/v1/internal/drivers/{driverId}/beneficiary")
    BeneficiaryResponse getDriverBeneficiary(@PathVariable("driverId") UUID driverId);
}
