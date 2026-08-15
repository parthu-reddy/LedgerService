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
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.ledger.dto.LedgerTransactionDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

@Service
@lombok.extern.slf4j.Slf4j
public class DoubleEntryLedgerService {
    @java.lang.SuppressWarnings("all")

    private final ILedgerAccountRepository accountRepository;
    private final ILedgerEntryRepository entryRepository;
    private final EntityManager entityManager;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Transactional
    public void recordTransaction(UUID transactionId, UUID sourceOwnerId, AccountType sourceOwnerType, UUID targetOwnerId, AccountType targetOwnerType, BigDecimal amount, ChargeCategory category) {
        recordTransaction(transactionId, null, sourceOwnerId, sourceOwnerType, targetOwnerId, targetOwnerType, amount, category);
    }

    @Transactional
    public void recordTransaction(UUID transactionId, UUID referenceId, UUID sourceOwnerId, AccountType sourceOwnerType, UUID targetOwnerId, AccountType targetOwnerType, BigDecimal amount, ChargeCategory category) {
        log.info("Recording ledger transaction: {} from {} ({}) to {} ({}) for amount {}", transactionId, sourceOwnerId, sourceOwnerType, targetOwnerId, targetOwnerType, amount);
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
        LedgerEntry debitEntry = LedgerEntry.builder().id(UUID.randomUUID()).transactionId(transactionId).referenceId(referenceId).accountId(sourceAccount.getId()).direction(com.fooddelivery.common.enums.TransactionDirection.DEBIT).category(category).amount(amount).createdAt(LocalDateTime.now()).build();
        entryRepository.save(debitEntry);
        
        // Credit Target
        if (targetOwnerType == AccountType.PLATFORM) {
            eventPublisher.publishEvent(new com.fooddelivery.ledger.event.DeferredBalanceUpdateEvent(targetAccount.getId(), amount));
        } else {
            accountRepository.updateBalance(targetAccount.getId(), amount);
        }
        LedgerEntry creditEntry = LedgerEntry.builder().id(UUID.randomUUID()).transactionId(transactionId).referenceId(referenceId).accountId(targetAccount.getId()).direction(com.fooddelivery.common.enums.TransactionDirection.CREDIT).category(category).amount(amount).createdAt(LocalDateTime.now()).build();
        entryRepository.save(creditEntry);
        log.info("Recorded double entry transaction {} for amount {}", transactionId, amount);
    }

    @Transactional
    public void recordBulkTransaction(UUID referenceId, List<com.fooddelivery.common.dto.LedgerEntryCommand> entries) {
        log.info("Recording bulk ledger transaction for reference: {}", referenceId);
        
        java.util.Set<String> uniqueAccounts = new java.util.HashSet<>();
        for (com.fooddelivery.common.dto.LedgerEntryCommand entry : entries) {
            uniqueAccounts.add(entry.getFromId() + "_" + entry.getFromType());
            uniqueAccounts.add(entry.getToId() + "_" + entry.getToType());
        }
        
        java.util.Map<String, LedgerAccount> accMap = new java.util.HashMap<>();
        for (String accStr : uniqueAccounts) {
            String[] parts = accStr.split("_");
            UUID ownerId = UUID.fromString(parts[0]);
            AccountType ownerType = AccountType.valueOf(parts[1]);
            accMap.put(accStr, getOrCreateAccount(ownerId, ownerType));
        }
        
        List<LedgerAccount> accountsToLock = accMap.values().stream()
            .filter(acc -> acc.getOwnerType() != AccountType.PLATFORM)
            .sorted(java.util.Comparator.comparing(LedgerAccount::getId))
            .collect(Collectors.toList());
            
        java.util.Map<UUID, LedgerAccount> lockedAccounts = new java.util.HashMap<>();
        for (LedgerAccount acc : accountsToLock) {
            lockedAccounts.put(acc.getId(), accountRepository.findByIdForUpdate(acc.getId()).orElseThrow());
        }
        for (LedgerAccount acc : accMap.values()) {
            if (acc.getOwnerType() == AccountType.PLATFORM) {
                lockedAccounts.put(acc.getId(), acc);
            }
        }

        for (com.fooddelivery.common.dto.LedgerEntryCommand command : entries) {
            UUID transactionId = UUID.randomUUID(); 
            LedgerAccount sourceAccount = lockedAccounts.get(accMap.get(command.getFromId() + "_" + command.getFromType()).getId());
            LedgerAccount targetAccount = lockedAccounts.get(accMap.get(command.getToId() + "_" + command.getToType()).getId());
            
            if (sourceAccount.getOwnerType() != AccountType.PLATFORM && sourceAccount.getBalance().compareTo(command.getAmount()) < 0) {
                log.error("Insufficient funds in source account {} for bulk transfer", sourceAccount.getId());
                throw new IllegalStateException("Insufficient funds in source account");
            }
            
            if (sourceAccount.getOwnerType() == AccountType.PLATFORM) {
                eventPublisher.publishEvent(new com.fooddelivery.ledger.event.DeferredBalanceUpdateEvent(sourceAccount.getId(), command.getAmount().negate()));
            } else {
                accountRepository.updateBalance(sourceAccount.getId(), command.getAmount().negate());
            }
            LedgerEntry debitEntry = LedgerEntry.builder().id(UUID.randomUUID()).transactionId(transactionId).referenceId(referenceId).accountId(sourceAccount.getId()).direction(com.fooddelivery.common.enums.TransactionDirection.DEBIT).category(command.getCategory()).amount(command.getAmount()).createdAt(LocalDateTime.now()).build();
            entryRepository.save(debitEntry);
            
            if (targetAccount.getOwnerType() == AccountType.PLATFORM) {
                eventPublisher.publishEvent(new com.fooddelivery.ledger.event.DeferredBalanceUpdateEvent(targetAccount.getId(), command.getAmount()));
            } else {
                accountRepository.updateBalance(targetAccount.getId(), command.getAmount());
            }
            LedgerEntry creditEntry = LedgerEntry.builder().id(UUID.randomUUID()).transactionId(transactionId).referenceId(referenceId).accountId(targetAccount.getId()).direction(com.fooddelivery.common.enums.TransactionDirection.CREDIT).category(command.getCategory()).amount(command.getAmount()).createdAt(LocalDateTime.now()).build();
            entryRepository.save(creditEntry);
        }
        log.info("Successfully recorded bulk transaction with {} entries for reference {}", entries.size(), referenceId);
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
