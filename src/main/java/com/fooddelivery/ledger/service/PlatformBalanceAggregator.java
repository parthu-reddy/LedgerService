package com.fooddelivery.ledger.service;

import com.fooddelivery.ledger.event.DeferredBalanceUpdateEvent;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class PlatformBalanceAggregator {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlatformBalanceAggregator.class);
    private final ILedgerAccountRepository accountRepository;

    public PlatformBalanceAggregator(ILedgerAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void handleDeferredBalanceUpdate(DeferredBalanceUpdateEvent event) {
        try {
            accountRepository.updateBalance(event.getAccountId(), event.getAmount());
            log.debug("Deferred balance update successful for account {}: amount {}", event.getAccountId(), event.getAmount());
        } catch (Exception e) {
            log.error("Failed to process deferred balance update for account {}", event.getAccountId(), e);
        }
    }
}
