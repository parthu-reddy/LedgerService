package com.fooddelivery.ledger.config;

import com.fooddelivery.common.client.CampaignServiceClient;
import com.fooddelivery.common.client.RestaurantServiceClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/** Register the real ownership clients used by the shared money policy, alongside ledger clients. */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {RestaurantServiceClient.class, CampaignServiceClient.class})
public class MoneyOwnershipClients { }
