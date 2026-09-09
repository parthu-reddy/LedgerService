package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.dto.PageResponseDto;
import com.fooddelivery.common.dto.ledger.PayoutDetailDto;
import com.fooddelivery.common.dto.ledger.PayoutDto;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.security.money.MoneyAccessPolicy;
import com.fooddelivery.common.security.money.MoneyOwnerType;
import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.mapper.PayoutMapper;
import com.fooddelivery.ledger.repository.PayoutLineRepository;
import com.fooddelivery.ledger.service.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ledger/payouts")
@RequiredArgsConstructor
public class PayeePayoutController {

    private final PayoutService payoutService;
    private final PayoutLineRepository payoutLineRepository;
    private final MoneyAccessPolicy moneyAccessPolicy;

    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESTAURANT', 'DELIVERY', 'ADMIN')")
    @GetMapping("/{payeeType}/{payeeId}")
    public ResponseEntity<PageResponseDto<PayoutDto>> getPayouts(
            @PathVariable String payeeType,
            @PathVariable UUID payeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
            
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        MoneyOwnerType moneyOwnerType = "RESTAURANT".equals(payeeType) ? MoneyOwnerType.RESTAURANT : MoneyOwnerType.DRIVER;
        
        if (!moneyAccessPolicy.canAccessMoney(authentication, moneyOwnerType, payeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Payout> payoutPage = payoutService.getPayouts(payeeType, payeeId, pageable);
        
        PageResponseDto<PayoutDto> response = PageResponseDto.<PayoutDto>builder()
                .content(payoutPage.getContent().stream().map(PayoutMapper::toDto).collect(Collectors.toList()))
                .number(payoutPage.getNumber())
                .size(payoutPage.getSize())
                .totalElements(payoutPage.getTotalElements())
                .totalPages(payoutPage.getTotalPages())
                .last(payoutPage.isLast())
                .first(payoutPage.isFirst())
                .numberOfElements(payoutPage.getNumberOfElements())
                .empty(payoutPage.isEmpty())
                .build();
        
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESTAURANT', 'DELIVERY', 'ADMIN')")
    @GetMapping("/{payeeType}/{payeeId}/{payoutId}")
    public ResponseEntity<PayoutDetailDto> getPayoutDetail(
            @PathVariable String payeeType,
            @PathVariable UUID payeeId,
            @PathVariable UUID payoutId) {
            
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        MoneyOwnerType moneyOwnerType = "RESTAURANT".equals(payeeType) ? MoneyOwnerType.RESTAURANT : MoneyOwnerType.DRIVER;
        
        if (!moneyAccessPolicy.canAccessMoney(authentication, moneyOwnerType, payeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        Payout payout = payoutService.getPayout(payoutId);
        if (!payout.getPayeeId().equals(payeeId) || !payout.getPayeeType().equals(payeeType)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        PayoutDetailDto detailDto = new PayoutDetailDto(
                PayoutMapper.toDto(payout),
                payoutLineRepository.findByPayoutId(payoutId).stream()
                        .map(PayoutMapper::toDto)
                        .collect(Collectors.toList())
        );
        return ResponseEntity.ok(detailDto);
    }
}
