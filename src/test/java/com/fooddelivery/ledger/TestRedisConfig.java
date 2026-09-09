package com.fooddelivery.ledger;

import org.mockito.Mockito;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

/**
 * A stand-in for Redis in tests.
 *
 * <p>{@code application-test.yml} excludes Redis autoconfiguration, so no {@link StringRedisTemplate}
 * exists — and {@code MoneyReconciliationJob} needs one to take the nightly lock it had always
 * claimed to take. Every {@code @SpringBootTest} in this service is component-scanned from
 * {@code com.fooddelivery.ledger}, so defining it here covers all of them.
 *
 * <p>A plain {@code @Configuration} rather than {@code @TestConfiguration}: the latter is excluded
 * from component scanning by design and would have to be imported by every test class. This lives in
 * {@code src/test}, so it is never packaged into the service.
 *
 * <p>The lock is granted: a test asserting the job's behaviour wants the job to run.
 */
@Configuration
public class TestRedisConfig {

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        Mockito.when(template.opsForValue()).thenReturn(ops);
        Mockito.when(ops.setIfAbsent(Mockito.anyString(), Mockito.anyString(), Mockito.any(Duration.class)))
               .thenReturn(true);
        return template;
    }
}
