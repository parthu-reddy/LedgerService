package com.fooddelivery.ledger.client;

import com.fooddelivery.common.dto.ledger.BeneficiaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BeneficiaryClient {

    private final RestaurantBeneficiaryFeignClient restaurantClient;
    private final DriverBeneficiaryFeignClient driverClient;

    public BeneficiaryResponse getBeneficiary(String payeeType, UUID payeeId) {
        try {
            if ("RESTAURANT".equals(payeeType)) {
                return restaurantClient.getBrandBeneficiary(payeeId);
            } else if ("DRIVER".equals(payeeType)) {
                return driverClient.getDriverBeneficiary(payeeId);
            } else {
                log.error("Unknown payee type: {}", payeeType);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to fetch beneficiary for {} {}: {}", payeeType, payeeId, e.getMessage());
            return null; // Fail closed logic handles null internally
        }
    }
}
