package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.security.money.MoneyAccessPolicy;
import com.fooddelivery.common.security.money.MoneyOwnerType;
import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.entity.PayoutStatus;
import com.fooddelivery.ledger.repository.PayoutLineRepository;
import com.fooddelivery.ledger.service.PayoutService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayeePayoutPrivacyTest {
    private final UUID payee = UUID.randomUUID();
    private final String bankSnapshot = "{\"accountNumber\":\"123456789012\",\"ifsc\":\"TEST0000001\"}";
    private final Payout payout = Payout.builder().id(UUID.randomUUID()).payeeType("RESTAURANT")
            .payeeId(payee).amount(new BigDecimal("125.50")).currency("INR")
            .status(PayoutStatus.APPROVED).beneficiarySnapshot(bankSnapshot).build();
    private final PayoutService service = mock(PayoutService.class);
    private final MoneyAccessPolicy policy = mock(MoneyAccessPolicy.class);
    private final PayoutLineRepository lines = mock(PayoutLineRepository.class);

    private PayeePayoutController controller(boolean mayManagePayouts) {
        var auth = new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(policy.canAccessMoney(auth, MoneyOwnerType.RESTAURANT, payee)).thenReturn(true);
        when(policy.canManagePayouts(auth, MoneyOwnerType.RESTAURANT, payee)).thenReturn(mayManagePayouts);
        when(service.getPayouts(eq("RESTAURANT"), eq(payee), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payout)));
        when(service.getPayout(payout.getId())).thenReturn(payout);
        when(lines.findByPayoutId(payout.getId())).thenReturn(List.of());
        return new PayeePayoutController(service, lines, policy);
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void earningsReaderGetsAmountsWithoutBankSnapshotInList() {
        var response = controller(false).getPayouts("RESTAURANT", payee, 0, 20).getBody();
        assertThat(response.getContent().get(0).getAmount()).isEqualByComparingTo("125.50");
        assertThat(response.getContent().get(0).getBeneficiarySnapshot()).isNull();
        assertThat(payout.getBeneficiarySnapshot()).isEqualTo(bankSnapshot);
    }

    @Test void earningsReaderGetsDetailsWithoutBankSnapshot() {
        var response = controller(false).getPayoutDetail("RESTAURANT", payee, payout.getId()).getBody();
        assertThat(response.getPayout().getAmount()).isEqualByComparingTo("125.50");
        assertThat(response.getPayout().getBeneficiarySnapshot()).isNull();
        assertThat(payout.getBeneficiarySnapshot()).isEqualTo(bankSnapshot);
    }

    @Test void payoutManagerKeepsTheAuthorisedBeneficiaryDetails() {
        var controller = controller(true);
        assertThat(controller.getPayouts("RESTAURANT", payee, 0, 20).getBody().getContent().get(0)
                .getBeneficiarySnapshot()).isEqualTo(bankSnapshot);
        assertThat(controller.getPayoutDetail("RESTAURANT", payee, payout.getId()).getBody().getPayout()
                .getBeneficiarySnapshot()).isEqualTo(bankSnapshot);
    }
}
