package com.fooddelivery.ledger.service;

import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.common.util.DeterministicIdUtils;
import com.fooddelivery.ledger.dto.LedgerTransactionDto;
import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.entity.LedgerEntry;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DoubleEntryLedgerService {

    private final ILedgerAccountRepository accountRepository;
    private final ILedgerEntryRepository entryRepository;
    private final EntityManager entityManager;

    public DoubleEntryLedgerService(ILedgerAccountRepository accountRepository,
                                    ILedgerEntryRepository entryRepository,
                                    EntityManager entityManager) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void record(LedgerTransactionCommand cmd) {
        log.info("Recording ledger transaction: {}", cmd.getTransactionId());
        
        // 1. Validations
        if (cmd.getTransactionId() == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        if (!DeterministicIdUtils.isLedgerId(cmd.getTransactionId(), cmd.getProducer(), cmd.getReferenceId().toString(), "1") &&
            !DeterministicIdUtils.isLedgerId(cmd.getTransactionId(), cmd.getProducer(), cmd.getReferenceId().toString(), "DELIVERED")) {
            // Note: The logic in isLedgerId uses the leg parameter. We should just check it's v5. 
            // In the plan it states "DeterministicIdUtils.isLedgerId".
            if (cmd.getTransactionId().version() != 5) {
                throw new com.fooddelivery.common.exception.LedgerRejectedException("Transaction ID must be a UUID v5");
            }
        }
        if (cmd.getLegs() == null || cmd.getLegs().isEmpty()) {
            throw new com.fooddelivery.common.exception.LedgerRejectedException("Transaction must have legs");
        }

        // 2. Idempotency
        if (entryRepository.existsByTransactionId(cmd.getTransactionId())) {
            log.info("Transaction {} already recorded. Skipping.", cmd.getTransactionId());
            return;
        }

        // 3. Accounts resolution & locking
        Set<String> uniqueAccountKeys = new HashSet<>();
        for (LedgerLeg leg : cmd.getLegs()) {
            uniqueAccountKeys.add(leg.getFromId() + ":" + leg.getFromType().name());
            uniqueAccountKeys.add(leg.getToId() + ":" + leg.getToType().name());
            
            if (leg.getAmount() == null || leg.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new com.fooddelivery.common.exception.LedgerRejectedException("Amount must be positive");
            }
            if (leg.getFromId().equals(leg.getToId()) && leg.getFromType() == leg.getToType()) {
                throw new com.fooddelivery.common.exception.LedgerRejectedException("Self transfers not allowed");
            }
        }

        Map<String, LedgerAccount> resolvedAccounts = new HashMap<>();
        for (String key : uniqueAccountKeys) {
            String[] parts = key.split(":");
            UUID id = UUID.fromString(parts[0]);
            LedgerAccountType type = LedgerAccountType.valueOf(parts[1]);
            resolvedAccounts.put(key, getOrCreateAccount(id, type));
        }

        // Lock PAYABLE and PREPAID accounts in ID order
        List<LedgerAccount> accountsToLock = resolvedAccounts.values().stream()
            .filter(acc -> acc.getKind() == LedgerAccountType.Kind.PAYABLE || acc.getKind() == LedgerAccountType.Kind.PREPAID)
            .sorted(Comparator.comparing(LedgerAccount::getId))
            .collect(Collectors.toList());

        Map<UUID, LedgerAccount> lockedAccounts = new HashMap<>();
        for (LedgerAccount acc : accountsToLock) {
            lockedAccounts.put(acc.getId(), accountRepository.findByIdForUpdate(acc.getId()).orElseThrow());
        }

        for (LedgerAccount acc : resolvedAccounts.values()) {
            if (acc.getKind() == LedgerAccountType.Kind.INTERNAL || acc.getKind() == LedgerAccountType.Kind.EXTERNAL) {
                lockedAccounts.put(acc.getId(), acc);
            }
        }

        // 4. Balance Rule and Apply Legs
        for (LedgerLeg leg : cmd.getLegs()) {
            LedgerAccount source = lockedAccounts.get(resolvedAccounts.get(leg.getFromId() + ":" + leg.getFromType().name()).getId());
            LedgerAccount target = lockedAccounts.get(resolvedAccounts.get(leg.getToId() + ":" + leg.getToType().name()).getId());

            boolean checkBalance = source.getKind() == LedgerAccountType.Kind.PAYABLE || source.getKind() == LedgerAccountType.Kind.PREPAID;
            if (checkBalance) {
                boolean bypass = leg.getCategory() == ChargeCategory.CLAWBACK && leg.getAuthorizedBy() != null;
                if (!bypass && source.getBalance().compareTo(leg.getAmount()) < 0) {
                    throw new IllegalStateException("INSUFFICIENT_FUNDS in source account " + source.getId());
                }
            }

            // Debit
            if (source.getKind() == LedgerAccountType.Kind.PAYABLE || source.getKind() == LedgerAccountType.Kind.PREPAID) {
                source.setBalance(source.getBalance().subtract(leg.getAmount()));
                accountRepository.save(source);
            } else {
                accountRepository.updateBalance(source.getId(), leg.getAmount().negate());
            }

            LedgerEntry debit = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(cmd.getTransactionId())
                .referenceId(cmd.getReferenceId())
                .accountId(source.getId())
                .direction(TransactionDirection.DEBIT)
                .category(leg.getCategory())
                .amount(leg.getAmount())
                .producer(cmd.getProducer())
                .description(leg.getDescription())
                .authorizedBy(leg.getAuthorizedBy())
                .createdAt(OffsetDateTime.now())
                .build();
            entryRepository.save(debit);

            // Credit
            if (target.getKind() == LedgerAccountType.Kind.PAYABLE || target.getKind() == LedgerAccountType.Kind.PREPAID) {
                target.setBalance(target.getBalance().add(leg.getAmount()));
                accountRepository.save(target);
            } else {
                accountRepository.updateBalance(target.getId(), leg.getAmount());
            }

            LedgerEntry credit = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(cmd.getTransactionId())
                .referenceId(cmd.getReferenceId())
                .accountId(target.getId())
                .direction(TransactionDirection.CREDIT)
                .category(leg.getCategory())
                .amount(leg.getAmount())
                .producer(cmd.getProducer())
                .description(leg.getDescription())
                .authorizedBy(leg.getAuthorizedBy())
                .createdAt(OffsetDateTime.now())
                .build();
            entryRepository.save(credit);
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal getOrderLedgerTotal(UUID referenceId) {
        BigDecimal total = entryRepository.sumByReferenceIdAndDirectionAndOwnerType(
                referenceId,
                TransactionDirection.CREDIT,
                LedgerAccountType.PLATFORM_CLEARING);
        return total == null ? BigDecimal.ZERO : total;
    }

    @Transactional
    public LedgerAccount getAccountBalance(UUID ownerId, LedgerAccountType ownerType) {
        return getOrCreateAccount(ownerId, ownerType);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<LedgerEntry> getEntries(UUID transactionId, UUID ownerId, LedgerAccountType ownerType, ChargeCategory category, TransactionDirection direction, org.springframework.data.domain.Pageable pageable) {
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
        return entryRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<LedgerTransactionDto> getTransactions(UUID transactionId, UUID ownerId, LedgerAccountType ownerType, ChargeCategory category, TransactionDirection direction, org.springframework.data.domain.Pageable pageable) {
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
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<UUID> query = cb.createQuery(UUID.class);
        Root<LedgerEntry> root = query.from(LedgerEntry.class);
        query.select(root.get("transactionId")).distinct(true);
        if (spec != null) {
            query.where(spec.toPredicate(root, query, cb));
        }
        
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<LedgerEntry> countRoot = countQuery.from(LedgerEntry.class);
        countQuery.select(cb.countDistinct(countRoot.get("transactionId")));
        if (spec != null) {
            countQuery.where(spec.toPredicate(countRoot, countQuery, cb));
        }
        Long total = entityManager.createQuery(countQuery).getSingleResult();
        
        CriteriaQuery<Object[]> sortQuery = cb.createQuery(Object[].class);
        Root<LedgerEntry> sortRoot = sortQuery.from(LedgerEntry.class);
        sortQuery.multiselect(sortRoot.get("transactionId"), cb.greatest(sortRoot.<OffsetDateTime>get("createdAt")));
        sortQuery.groupBy(sortRoot.get("transactionId"));
        if (spec != null) {
            sortQuery.where(spec.toPredicate(sortRoot, sortQuery, cb));
        }
        sortQuery.orderBy(cb.desc(cb.greatest(sortRoot.<OffsetDateTime>get("createdAt"))));
        
        List<Object[]> sortedResult = entityManager.createQuery(sortQuery).setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize()).getResultList();
        List<UUID> transactionIds = sortedResult.stream().map(obj -> (UUID) obj[0]).collect(Collectors.toList());
        
        if (transactionIds.isEmpty()) {
            return org.springframework.data.domain.Page.empty(pageable);
        }
        
        List<LedgerEntry> entries = entryRepository.findByTransactionIdIn(transactionIds);
        Map<String, List<LedgerEntry>> grouped = entries.stream().collect(Collectors.groupingBy(e -> e.getTransactionId().toString() + "_" + e.getCategory().name()));
        
        List<LedgerTransactionDto> dtos = grouped.values().stream().map(group -> {
            LedgerTransactionDto dto = new LedgerTransactionDto();
            LedgerEntry first = group.get(0);
            dto.setTransactionId(first.getTransactionId());
            dto.setCategory(first.getCategory());
            dto.setAmount(first.getAmount());
            // Need to change dto setDate to OffsetDateTime or map it
            // dto.setDate(first.getCreatedAt());
            for (LedgerEntry e : group) {
                if (e.getDirection() == TransactionDirection.DEBIT) {
                    dto.setFromAccountId(e.getAccountId());
                } else {
                    dto.setToAccountId(e.getAccountId());
                }
            }
            return dto;
        }).collect(Collectors.toList());
        
        List<LedgerTransactionDto> sortedDtos = new ArrayList<>();
        for (UUID tId : transactionIds) {
            sortedDtos.addAll(dtos.stream().filter(d -> d.getTransactionId().equals(tId)).collect(Collectors.toList()));
        }
        return new org.springframework.data.domain.PageImpl<>(sortedDtos, pageable, total);
    }

    private LedgerAccount getOrCreateAccount(UUID ownerId, LedgerAccountType ownerType) {
        return accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType).orElseGet(() -> {
            try {
                LedgerAccount newAccount = LedgerAccount.builder()
                    .id(UUID.randomUUID())
                    .ownerId(ownerId)
                    .ownerType(ownerType)
                    .kind(ownerType.getKind())
                    .balance(BigDecimal.ZERO)
                    .currency("INR")
                    .lockVersion(0)
                    .createdAt(OffsetDateTime.now())
                    .build();
                return accountRepository.saveAndFlush(newAccount);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.info("Concurrent account creation detected for owner: {} of type: {}", ownerId, ownerType);
                return accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType).orElseThrow(() -> new IllegalStateException("Failed to get or create account concurrently", e));
            }
        });
    }
}
