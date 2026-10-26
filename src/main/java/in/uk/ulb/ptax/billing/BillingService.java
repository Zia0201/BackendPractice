package in.uk.ulb.ptax.billing;

import in.uk.ulb.ptax.auth.AuthUser;
import in.uk.ulb.ptax.common.ApiException;
import in.uk.ulb.ptax.common.Role;
import in.uk.ulb.ptax.mutation.MutationApplication;
import in.uk.ulb.ptax.mutation.MutationRepository;
import in.uk.ulb.ptax.notification.NotificationService;
import in.uk.ulb.ptax.property.Property;
import in.uk.ulb.ptax.property.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillingService {

    private static final Set<String> PAYMENT_MODES = Set.of("CASH", "UPI", "CARD", "NETBANKING", "CHEQUE");

    private final DemandRepository demandRepository;
    private final PaymentRepository paymentRepository;
    private final PropertyRepository propertyRepository;
    private final MutationRepository mutationRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PropertyAccount accountFor(Long propertyId) {
        List<Demand> demands = demandRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId);
        List<Long> ids = demands.stream().map(Demand::getId).toList();
        List<Payment> payments = ids.isEmpty() ? List.of() : paymentRepository.findByDemandIdInOrderByPaidAtDesc(ids);

        Map<Long, Demand> byId = demands.stream().collect(Collectors.toMap(Demand::getId, d -> d));
        BigDecimal outstanding = demands.stream().map(Demand::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PropertyAccount(
                demands.stream().map(DemandView::of).toList(),
                payments.stream().map(p -> PaymentView.of(p, byId.get(p.getDemandId()))).toList(),
                outstanding);
    }

    public BigDecimal outstanding(Long propertyId) {
        return demandRepository.outstandingFor(propertyId);
    }

    /**
     * Partial payment is allowed (meeting requirement) - anything from Re 1 up to the balance.
     */
    @Transactional
    public PaymentView pay(AuthUser user, Long demandId, BigDecimal amount, String mode) {
        user.require(Role.COUNTER, Role.CLERK, Role.CITIZEN);

        Demand demand = demandRepository.findForUpdate(demandId)
                .orElseThrow(() -> ApiException.notFound("Demand"));
        Property property = propertyRepository.findDetailed(demand.getPropertyId())
                .orElseThrow(() -> ApiException.notFound("Property"));

        if (user.role() != Role.CITIZEN) {
            user.checkUlb(property.getUlb().getId());
        }

        String payMode = mode == null ? "CASH" : mode.toUpperCase();
        if (!PAYMENT_MODES.contains(payMode)) {
            throw ApiException.badRequest("Unknown payment mode " + mode);
        }
        if (user.role() == Role.CITIZEN && payMode.equals("CASH")) {
            throw ApiException.badRequest("Cash can only be collected at the counter");
        }

        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ONE) < 0) {
            throw ApiException.badRequest("Amount should be at least Rs 1");
        }
        if (amount.compareTo(demand.getBalance()) > 0) {
            throw ApiException.badRequest("Amount is more than the balance of Rs " + demand.getBalance());
        }

        demand.setPaidAmount(demand.getPaidAmount().add(amount));

        Payment payment = new Payment();
        payment.setDemandId(demand.getId());
        payment.setAmount(amount);
        payment.setMode(payMode);
        payment.setCollectedBy(user.id());
        payment.setReceiptNo(String.format("RCPT-%02d-%06d", LocalDate.now().getYear() % 100,
                paymentRepository.nextReceiptNumber()));
        paymentRepository.save(payment);

        String msg = String.format("Rs %s received against %s for %s. Receipt %s. Balance Rs %s. - %s",
                amount, describe(demand), property.getPropertyNo(), payment.getReceiptNo(),
                demand.getBalance(), property.getUlb().getName());

        if (demand.getDemandType() == DemandType.MUTATION_FEE && demand.getMutationId() != null) {
            MutationApplication app = mutationRepository.findById(demand.getMutationId()).orElseThrow();
            notificationService.sms(app.getNewOwnerMobile(), msg, property.getUlb().getId(), "PAYMENT", payment.getId());
        } else {
            notificationService.sms(property.getOwnerMobile(), msg, property.getUlb().getId(), "PAYMENT", payment.getId());
        }

        return PaymentView.of(payment, demand);
    }

    private static String describe(Demand d) {
        return d.getDemandType() == DemandType.MUTATION_FEE
                ? "mutation fee"
                : "property tax " + d.getFinancialYear();
    }

    public record PropertyAccount(List<DemandView> demands, List<PaymentView> payments, BigDecimal outstanding) {
    }

    public record DemandView(Long id, DemandType type, String financialYear, Long mutationId,
                             BigDecimal amount, BigDecimal paid, BigDecimal balance, LocalDateTime createdAt) {
        static DemandView of(Demand d) {
            return new DemandView(d.getId(), d.getDemandType(), d.getFinancialYear(), d.getMutationId(),
                    d.getAmount(), d.getPaidAmount(), d.getBalance(), d.getCreatedAt());
        }
    }

    public record PaymentView(Long id, String receiptNo, Long demandId, String against,
                              BigDecimal amount, String mode, LocalDateTime paidAt) {
        static PaymentView of(Payment p, Demand d) {
            return new PaymentView(p.getId(), p.getReceiptNo(), p.getDemandId(), d == null ? "" : describe(d),
                    p.getAmount(), p.getMode(), p.getPaidAt());
        }
    }
}
