package com.fooddelivery.ledger.service;

import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.entity.LedgerEntry;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import com.fooddelivery.common.enums.AccountType;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoubleEntryLedgerService {

    private final ILedgerAccountRepository accountRepository;
    private final ILedgerEntryRepository entryRepository;

    @Transactional
    public void recordTransaction(UUID transactionId, UUID sourceOwnerId, AccountType sourceOwnerType, 
                                  UUID targetOwnerId, AccountType targetOwnerType, BigDecimal amount,
                                  com.fooddelivery.common.enums.ChargeCategory category) {
        if (amount == null) {
            log.error("Ledger transaction amount is null for transactionId: {}", transactionId);
            throw new IllegalArgumentException("Transaction amount cannot be null. Strict policy requires valid amounts.");
        }
        if (category == null) {
            log.error("Ledger transaction category is null for transactionId: {}", transactionId);
            throw new IllegalArgumentException("Transaction category cannot be null. Strict policy requires valid categorization.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        if (sourceOwnerId.equals(targetOwnerId) && sourceOwnerType == targetOwnerType) {
            throw new IllegalArgumentException("Source and target accounts cannot be the same. Self-transfers are rejected.");
        }

        if (entryRepository.existsByTransactionId(transactionId)) {
            log.info("Transaction {} already recorded. Skipping.", transactionId);
            return;
        }

        LedgerAccount sourceAccount = getOrCreateAccount(sourceOwnerId, sourceOwnerType);
        LedgerAccount targetAccount = getOrCreateAccount(targetOwnerId, targetOwnerType);

        // Debit Source
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        accountRepository.save(sourceAccount);
        
        LedgerEntry debitEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .accountId(sourceAccount.getId())
                .direction(com.fooddelivery.common.enums.TransactionDirection.DEBIT)
                .category(category)
                .amount(amount)
                .createdAt(LocalDateTime.now())
                .build();
        entryRepository.save(debitEntry);

        // Credit Target
        targetAccount.setBalance(targetAccount.getBalance().add(amount));
        accountRepository.save(targetAccount);

        LedgerEntry creditEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .accountId(targetAccount.getId())
                .direction(com.fooddelivery.common.enums.TransactionDirection.CREDIT)
                .category(category)
                .amount(amount)
                .createdAt(LocalDateTime.now())
                .build();
        entryRepository.save(creditEntry);
        
        log.info("Recorded double entry transaction {} for amount {}", transactionId, amount);
    }

    public LedgerAccount getAccountBalance(UUID ownerId, AccountType ownerType) {
        return getOrCreateAccount(ownerId, ownerType);
    }

    public org.springframework.data.domain.Page<LedgerEntry> getEntries(
            UUID transactionId,
            UUID ownerId,
            AccountType ownerType,
            com.fooddelivery.common.enums.ChargeCategory category,
            com.fooddelivery.common.enums.TransactionDirection direction,
            org.springframework.data.domain.Pageable pageable) {

        UUID accountId = null;
        if (ownerId != null && ownerType != null) {
            java.util.Optional<LedgerAccount> accountOpt = accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType);
            if (accountOpt.isPresent()) {
                accountId = accountOpt.get().getId();
            } else {
                // If account does not exist, they have no entries
                return org.springframework.data.domain.Page.empty(pageable);
            }
        }

        org.springframework.data.jpa.domain.Specification<LedgerEntry> spec = 
                com.fooddelivery.ledger.repository.LedgerEntrySpecification.filterBy(transactionId, accountId, category, direction);

        return entryRepository.findAll(spec, pageable);
    }

    private LedgerAccount getOrCreateAccount(UUID ownerId, AccountType ownerType) {
        return accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType)
                .orElseGet(() -> {
                    try {
                        LedgerAccount newAccount = LedgerAccount.builder()
                                .id(UUID.randomUUID())
                                .ownerId(ownerId)
                                .ownerType(ownerType)
                                .balance(BigDecimal.ZERO)
                                .build();
                        return accountRepository.saveAndFlush(newAccount);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        log.info("Concurrent account creation detected for owner: {} of type: {}", ownerId, ownerType);
                        return accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType)
                                .orElseThrow(() -> new IllegalStateException("Failed to get or create account concurrently", e));
                    }
                });
    }
}
