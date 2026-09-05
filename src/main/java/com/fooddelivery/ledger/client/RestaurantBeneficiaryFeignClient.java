package com.fooddelivery.ledger.client;

import com.fooddelivery.common.dto.ledger.BeneficiaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "restaurant-service")
public interface RestaurantBeneficiaryFeignClient {
    @GetMapping("/api/v1/internal/restaurants/{brandId}/beneficiary")
    BeneficiaryResponse getBrandBeneficiary(@PathVariable("brandId") UUID brandId);
}
