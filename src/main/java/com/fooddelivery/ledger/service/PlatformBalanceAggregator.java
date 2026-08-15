package com.fooddelivery.ledger.service;

import com.fooddelivery.ledger.event.DeferredBalanceUpdateEvent;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import com.fooddelivery.ledger.repository.IFailedDeferredUpdateRepository;
import com.fooddelivery.ledger.entity.FailedDeferredUpdate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
public class PlatformBalanceAggregator {

    private final ILedgerAccountRepository accountRepository;
    private final IFailedDeferredUpdateRepository failedUpdateRepository;

    public PlatformBalanceAggregator(ILedgerAccountRepository accountRepository, IFailedDeferredUpdateRepository failedUpdateRepository) {
        this.accountRepository = accountRepository;
        this.failedUpdateRepository = failedUpdateRepository;
    }

    @Async
    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Retryable(
      value = { Exception.class }, 
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void handleDeferredBalanceUpdate(DeferredBalanceUpdateEvent event) {
        accountRepository.updateBalance(event.getAccountId(), event.getAmount());
        log.debug("Deferred balance update successful for account {}: amount {}", event.getAccountId(), event.getAmount());
    }

    @Recover
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recoverDeferredBalanceUpdate(Exception e, DeferredBalanceUpdateEvent event) {
        log.error("Exhausted retries for deferred balance update. Saving to DLQ. Account: {}", event.getAccountId(), e);
        FailedDeferredUpdate failedUpdate = new FailedDeferredUpdate(
            UUID.randomUUID(),
            event.getAccountId(),
            event.getAmount(),
            LocalDateTime.now(),
            e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 999)) : "Unknown Error",
            false
        );
        failedUpdateRepository.save(failedUpdate);
    }
}
