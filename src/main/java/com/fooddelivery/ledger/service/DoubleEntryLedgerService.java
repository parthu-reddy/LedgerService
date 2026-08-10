package com.fooddelivery.ledger.service;

import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.entity.LedgerEntry;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fooddelivery.common.enums.AccountType;
import com.fooddelivery.ledger.dto.LedgerTransactionDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

@Service
public class DoubleEntryLedgerService {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DoubleEntryLedgerService.class);
    private final ILedgerAccountRepository accountRepository;
    private final ILedgerEntryRepository entryRepository;
    private final EntityManager entityManager;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional
    public void recordTransaction(UUID transactionId, UUID sourceOwnerId, AccountType sourceOwnerType, UUID targetOwnerId, AccountType targetOwnerType, BigDecimal amount, com.fooddelivery.common.enums.ChargeCategory category) {
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

        boolean sourceFirst = sourceOwnerId.compareTo(targetOwnerId) < 0;
        LedgerAccount sourceAccount;
        LedgerAccount targetAccount;
        
        if (sourceFirst) {
            sourceAccount = getAccountForTransaction(sourceOwnerId, sourceOwnerType);
            targetAccount = getAccountForTransaction(targetOwnerId, targetOwnerType);
        } else {
            targetAccount = getAccountForTransaction(targetOwnerId, targetOwnerType);
            sourceAccount = getAccountForTransaction(sourceOwnerId, sourceOwnerType);
        }

        // Rule: Avoid negative balances for standard accounts (System/Platform accounts are exempt)
        if (sourceOwnerType != AccountType.PLATFORM && sourceAccount.getBalance().compareTo(amount) < 0) {
            log.error("Insufficient funds in source account {} for transaction {}", sourceAccount.getId(), transactionId);
            throw new IllegalStateException("Insufficient funds in source account");
        }

        // Debit Source
        if (sourceOwnerType == AccountType.PLATFORM) {
            eventPublisher.publishEvent(new com.fooddelivery.ledger.event.DeferredBalanceUpdateEvent(sourceAccount.getId(), amount.negate()));
        } else {
            accountRepository.updateBalance(sourceAccount.getId(), amount.negate());
        }
        LedgerEntry debitEntry = LedgerEntry.builder().id(UUID.randomUUID()).transactionId(transactionId).accountId(sourceAccount.getId()).direction(com.fooddelivery.common.enums.TransactionDirection.DEBIT).category(category).amount(amount).createdAt(LocalDateTime.now()).build();
        entryRepository.save(debitEntry);
        
        // Credit Target
        if (targetOwnerType == AccountType.PLATFORM) {
            eventPublisher.publishEvent(new com.fooddelivery.ledger.event.DeferredBalanceUpdateEvent(targetAccount.getId(), amount));
        } else {
            accountRepository.updateBalance(targetAccount.getId(), amount);
        }
        LedgerEntry creditEntry = LedgerEntry.builder().id(UUID.randomUUID()).transactionId(transactionId).accountId(targetAccount.getId()).direction(com.fooddelivery.common.enums.TransactionDirection.CREDIT).category(category).amount(amount).createdAt(LocalDateTime.now()).build();
        entryRepository.save(creditEntry);
        log.info("Recorded double entry transaction {} for amount {}", transactionId, amount);
    }

    @Transactional
    public void reverseTransaction(UUID originalTransactionId, UUID reversalTransactionId, String reason) {
        reverseTransaction(originalTransactionId, reversalTransactionId, reason, null);
    }

    @Transactional
    public void reverseTransaction(UUID originalTransactionId, UUID reversalTransactionId, String reason, BigDecimal partialAmount) {
        log.info("Reversing transaction {} with reversal id {} due to: {}", originalTransactionId, reversalTransactionId, reason);
        if (entryRepository.existsByTransactionId(reversalTransactionId)) {
            log.info("Reversal transaction {} already recorded. Skipping.", reversalTransactionId);
            return;
        }

        List<LedgerEntry> originalEntries = entryRepository.findByTransactionId(originalTransactionId);
        if (originalEntries.isEmpty()) {
            throw new IllegalStateException("Original transaction " + originalTransactionId + " not found. Cannot reverse.");
        }

        if (originalEntries.size() != 2) {
            throw new IllegalStateException("Original transaction " + originalTransactionId + " does not have exactly 2 entries. Invalid state.");
        }

        LedgerEntry entry1 = originalEntries.get(0);
        LedgerEntry entry2 = originalEntries.get(1);

        // Deterministic locking to avoid deadlocks
        boolean entry1First = entry1.getAccountId().compareTo(entry2.getAccountId()) < 0;
        LedgerAccount account1;
        LedgerAccount account2;
        if (entry1First) {
            account1 = accountRepository.findByIdForUpdate(entry1.getAccountId()).orElseThrow();
            account2 = accountRepository.findByIdForUpdate(entry2.getAccountId()).orElseThrow();
        } else {
            account2 = accountRepository.findByIdForUpdate(entry2.getAccountId()).orElseThrow();
            account1 = accountRepository.findByIdForUpdate(entry1.getAccountId()).orElseThrow();
        }

        for (LedgerEntry entry : originalEntries) {
            // Reverse direction
            com.fooddelivery.common.enums.TransactionDirection reverseDirection = 
                entry.getDirection() == com.fooddelivery.common.enums.TransactionDirection.DEBIT 
                    ? com.fooddelivery.common.enums.TransactionDirection.CREDIT 
                    : com.fooddelivery.common.enums.TransactionDirection.DEBIT;
            
            BigDecimal originalAmountToReverse = (partialAmount != null) ? partialAmount : entry.getAmount();

            // Adjust balance
            BigDecimal amountAdjustment = reverseDirection == com.fooddelivery.common.enums.TransactionDirection.CREDIT 
                ? originalAmountToReverse 
                : originalAmountToReverse.negate();
                
            LedgerAccount account = entry.getAccountId().equals(account1.getId()) ? account1 : account2;
            if (amountAdjustment.compareTo(BigDecimal.ZERO) < 0 && account.getOwnerType() != AccountType.PLATFORM && account.getBalance().compareTo(amountAdjustment.negate()) < 0) {
                log.error("Insufficient funds in account {} for reversal {}", account.getId(), reversalTransactionId);
                throw new IllegalStateException("Insufficient funds for reversal");
            }

            accountRepository.updateBalance(entry.getAccountId(), amountAdjustment);

            LedgerEntry reversalEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(reversalTransactionId)
                .accountId(entry.getAccountId())
                .direction(reverseDirection)
                .category(entry.getCategory())
                .amount(originalAmountToReverse)
                .createdAt(LocalDateTime.now())
                .build();
            
            entryRepository.save(reversalEntry);
        }
        
        log.info("Successfully reversed transaction {}", originalTransactionId);
    }

    @Transactional
    public LedgerAccount getAccountBalance(UUID ownerId, AccountType ownerType) {
        return getOrCreateAccount(ownerId, ownerType);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<LedgerEntry> getEntries(UUID transactionId, UUID ownerId, AccountType ownerType, com.fooddelivery.common.enums.ChargeCategory category, com.fooddelivery.common.enums.TransactionDirection direction, org.springframework.data.domain.Pageable pageable) {
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
        org.springframework.data.jpa.domain.Specification<LedgerEntry> spec = com.fooddelivery.ledger.repository.LedgerEntrySpecification.filterBy(transactionId, accountId, category, direction);
        return entryRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<LedgerTransactionDto> getTransactions(UUID transactionId, UUID ownerId, AccountType ownerType, com.fooddelivery.common.enums.ChargeCategory category, com.fooddelivery.common.enums.TransactionDirection direction, org.springframework.data.domain.Pageable pageable) {
        UUID accountId = null;
        if (ownerId != null && ownerType != null) {
            java.util.Optional<LedgerAccount> accountOpt = accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType);
            if (accountOpt.isPresent()) {
                accountId = accountOpt.get().getId();
            } else {
                return org.springframework.data.domain.Page.empty(pageable);
            }
        }
        org.springframework.data.jpa.domain.Specification<LedgerEntry> spec = com.fooddelivery.ledger.repository.LedgerEntrySpecification.filterBy(transactionId, accountId, category, direction);
        // 1. Get Distinct Transaction IDs using CriteriaBuilder
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<UUID> query = cb.createQuery(UUID.class);
        Root<LedgerEntry> root = query.from(LedgerEntry.class);
        query.select(root.get("transactionId")).distinct(true);
        if (spec != null) {
            query.where(spec.toPredicate(root, query, cb));
        }
        // Count distinct query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<LedgerEntry> countRoot = countQuery.from(LedgerEntry.class);
        countQuery.select(cb.countDistinct(countRoot.get("transactionId")));
        if (spec != null) {
            countQuery.where(spec.toPredicate(countRoot, countQuery, cb));
        }
        Long total = entityManager.createQuery(countQuery).getSingleResult();
        // Fetch paginated distinct IDs (we need to order by the date, so we sort by Max createdAt)
        CriteriaQuery<Object[]> sortQuery = cb.createQuery(Object[].class);
        Root<LedgerEntry> sortRoot = sortQuery.from(LedgerEntry.class);
        sortQuery.multiselect(sortRoot.get("transactionId"), cb.greatest(sortRoot.<LocalDateTime>get("createdAt")));
        sortQuery.groupBy(sortRoot.get("transactionId"));
        if (spec != null) {
            sortQuery.where(spec.toPredicate(sortRoot, sortQuery, cb));
        }
        sortQuery.orderBy(cb.desc(cb.greatest(sortRoot.<LocalDateTime>get("createdAt"))));
        List<Object[]> sortedResult = entityManager.createQuery(sortQuery).setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize()).getResultList();
        List<UUID> transactionIds = sortedResult.stream().map(obj -> (UUID) obj[0]).collect(Collectors.toList());
        if (transactionIds.isEmpty()) {
            return org.springframework.data.domain.Page.empty(pageable);
        }
        // 2. Fetch all entries for these IDs
        List<LedgerEntry> entries = entryRepository.findByTransactionIdIn(transactionIds);
        // 3. Group by transactionId and category
        Map<String, List<LedgerEntry>> grouped = entries.stream().collect(Collectors.groupingBy(e -> e.getTransactionId().toString() + "_" + e.getCategory().name()));
        List<LedgerTransactionDto> dtos = grouped.values().stream().map(group -> {
            LedgerTransactionDto dto = new LedgerTransactionDto();
            LedgerEntry first = group.get(0);
            dto.setTransactionId(first.getTransactionId());
            dto.setCategory(first.getCategory());
            dto.setAmount(first.getAmount());
            dto.setDate(first.getCreatedAt());
            for (LedgerEntry e : group) {
                if (e.getDirection() == com.fooddelivery.common.enums.TransactionDirection.DEBIT) {
                    dto.setFromAccountId(e.getAccountId());
                } else {
                    dto.setToAccountId(e.getAccountId());
                }
            }
            return dto;
        }).collect(Collectors.toList());
        // Maintain the sorted order returned by the distinct query
        List<LedgerTransactionDto> sortedDtos = new java.util.ArrayList<>();
        for (UUID tId : transactionIds) {
            sortedDtos.addAll(dtos.stream().filter(d -> d.getTransactionId().equals(tId)).collect(Collectors.toList()));
        }
        return new org.springframework.data.domain.PageImpl<>(sortedDtos, pageable, total);
    }

    private LedgerAccount getOrCreateAccount(UUID ownerId, AccountType ownerType) {
        return accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType).orElseGet(() -> {
            try {
                LedgerAccount newAccount = LedgerAccount.builder().id(UUID.randomUUID()).ownerId(ownerId).ownerType(ownerType).balance(BigDecimal.ZERO).build();
                return accountRepository.saveAndFlush(newAccount);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.info("Concurrent account creation detected for owner: {} of type: {}", ownerId, ownerType);
                return accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType).orElseThrow(() -> new IllegalStateException("Failed to get or create account concurrently", e));
            }
        });
    }

    private LedgerAccount getOrCreateAccountForUpdate(UUID ownerId, AccountType ownerType) {
        return accountRepository.findByOwnerIdAndOwnerTypeForUpdate(ownerId, ownerType).orElseGet(() -> {
            getOrCreateAccount(ownerId, ownerType);
            return accountRepository.findByOwnerIdAndOwnerTypeForUpdate(ownerId, ownerType)
                .orElseThrow(() -> new IllegalStateException("Failed to get or create account concurrently"));
        });
    }

    private LedgerAccount getAccountForTransaction(UUID ownerId, AccountType ownerType) {
        if (ownerType == AccountType.PLATFORM) {
            return getOrCreateAccount(ownerId, ownerType); // Skip pessimistic lock for hot accounts
        }
        return getOrCreateAccountForUpdate(ownerId, ownerType);
    }

    @java.lang.SuppressWarnings("all")
    public DoubleEntryLedgerService(final ILedgerAccountRepository accountRepository, final ILedgerEntryRepository entryRepository, final EntityManager entityManager, final org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
    }
}
