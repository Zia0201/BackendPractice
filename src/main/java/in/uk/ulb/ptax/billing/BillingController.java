package in.uk.ulb.ptax.billing;

import in.uk.ulb.ptax.auth.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/demands")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/{demandId}/payments")
    public BillingService.PaymentView pay(AuthUser user, @PathVariable Long demandId,
                                          @Valid @RequestBody PaymentRequest req) {
        return billingService.pay(user, demandId, req.amount(), req.mode());
    }

    public record PaymentRequest(@NotNull @Positive BigDecimal amount, String mode) {
    }
}
