package com.fooddelivery.ledger.service;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class OwnerNameResolver {
    public String resolveDisplayName(String ownerType, UUID ownerId) {
        // In a full implementation, this would call Restaurant/Driver services to get the real display name
        return ownerType + " " + ownerId.toString().substring(0, 8);
    }
}
