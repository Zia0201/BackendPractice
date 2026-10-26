package in.uk.ulb.ptax.mutation;

import in.uk.ulb.ptax.billing.DemandRepository;
import in.uk.ulb.ptax.common.ApiException;
import in.uk.ulb.ptax.notification.NotificationService;
import in.uk.ulb.ptax.property.Property;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NoDuesService {

    private final DemandRepository demandRepository;
    private final NoDuesCertificateRepository certificateRepository;
    private final NotificationService notificationService;

    /**
     * Issues the certificate and sends it to the owner and to the registration department
     * (they asked for no-dues clarification before registering any sale).
     */
    @Transactional
    public NoDuesCertificate issue(Property property, Long mutationId) {
        BigDecimal outstanding = demandRepository.outstandingFor(property.getId());
        if (outstanding.signum() > 0) {
            throw ApiException.badRequest("Rs " + outstanding + " is still due on " + property.getPropertyNo());
        }

        NoDuesCertificate cert = new NoDuesCertificate();
        cert.setPropertyId(property.getId());
        cert.setMutationId(mutationId);
        cert.setCertificateNo(String.format("NDC-%s-%d-%05d", property.getUlb().getCode(),
                LocalDate.now().getYear(), certificateRepository.nextCertificateNumber()));
        cert.setValidTill(LocalDate.now().plusMonths(3));
        certificateRepository.save(cert);

        String msg = "No dues certificate " + cert.getCertificateNo() + " issued for property " + property.getPropertyNo()
                + " (" + property.getOwnerName() + "), valid till " + cert.getValidTill() + ". - " + property.getUlb().getName();
        Long ulbId = property.getUlb().getId();
        notificationService.smsAndEmail(property.getOwnerMobile(), property.getOwnerEmail(), msg, ulbId, "NO_DUES", cert.getId());
        notificationService.email(NotificationService.REGISTRATION_DEPT_EMAIL, msg, ulbId, "NO_DUES", cert.getId());
        return cert;
    }

    public record NoDuesStatus(String propertyNo, String ownerName, BigDecimal outstanding, boolean clear,
                               String latestCertificateNo, LocalDateTime issuedAt) {
    }
}
