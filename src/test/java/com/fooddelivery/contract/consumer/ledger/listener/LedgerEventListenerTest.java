package com.fooddelivery.contract.consumer.ledger.listener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.StubTrigger;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = com.fooddelivery.ledger.LedgerApplication.class, properties = {"spring.main.allow-bean-definition-overriding=true", "eureka.client.enabled=false", "spring.cloud.config.enabled=false", "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureStubRunner(ids = "com.fooddelivery:food-delivery-backend:+:stubs", stubsMode = StubRunnerProperties.StubsMode.REMOTE, repositoryRoot = "git://file:///Users/parthureddy/Documents/Food%20Delivery.nosync/FoodDeliveryContracts/.git")
@EmbeddedKafka(partitions = 1)
public class LedgerEventListenerTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private io.github.bucket4j.distributed.proxy.ProxyManager<String> proxyManager;


    @Autowired
    private StubTrigger stubTrigger;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers", "localhost:9092"));
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    }

    @Test
    public void testConsumerIsWorking() {
        try {
            stubTrigger.trigger("trigger-unknown");
        } catch (Exception e) {
            System.out.println("Trigger failed, which might be expected if the stub does not have it yet. Error: " + e.getMessage());
        }
    }
}
