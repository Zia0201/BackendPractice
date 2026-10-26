package in.uk.ulb.ptax.property;

import in.uk.ulb.ptax.auth.AuthUser;
import in.uk.ulb.ptax.billing.BillingService;
import in.uk.ulb.ptax.common.ApiException;
import in.uk.ulb.ptax.common.Role;
import in.uk.ulb.ptax.mutation.MutationRepository;
import in.uk.ulb.ptax.mutation.NoDuesCertificate;
import in.uk.ulb.ptax.mutation.NoDuesCertificateRepository;
import in.uk.ulb.ptax.mutation.NoDuesService;
import in.uk.ulb.ptax.notification.NotificationService;
import in.uk.ulb.ptax.tax.AssessmentService;
import in.uk.ulb.ptax.ulb.Ulb;
import in.uk.ulb.ptax.ulb.UlbRepository;
import in.uk.ulb.ptax.ulb.Ward;
import in.uk.ulb.ptax.ulb.WardRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final WardRepository wardRepository;
    private final UlbRepository ulbRepository;
    private final BillingService billingService;
    private final AssessmentService assessmentService;
    private final MutationRepository mutationRepository;
    private final NoDuesCertificateRepository certificateRepository;
    private final NoDuesService noDuesService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<PropertyView> search(AuthUser user, Long ulbId, Long wardId, String q) {
        List<Property> result;
        if (user.role() == Role.CITIZEN) {
            result = propertyRepository.findOwnedBy(user.mobile());
        } else {
            Long ulb = user.role() == Role.ADMIN ? ulbId : user.ulbId();
            String term = q == null ? "" : q.trim().toLowerCase();
            result = propertyRepository.search(ulb, wardId, term, PageRequest.of(0, 200));
        }
        return result.stream().map(PropertyView::of).toList();
    }

    @Transactional(readOnly = true)
    public PropertyDetail detail(AuthUser user, Long id) {
        Property p = load(id);
        checkCanView(user, p);

        return new PropertyDetail(
                PropertyView.of(p),
                p.getOwnerMobile(),
                p.getOwnerEmail(),
                p.getPlotArea(), p.getCoveredArea(), p.getCarpetArea(), p.getConstructionYear(),
                p.getLastMutationDate(), p.getCreatedAt(),
                billingService.accountFor(id),
                assessmentService.history(id),
                mutationRepository.findByPropertyIdOrderByCreatedAtDesc(id).stream()
                        .map(m -> new MutationRef(m.getId(), m.getApplicationNo(), m.getStatus().name(),
                                m.getNewOwnerName(), m.getCreatedAt()))
                        .toList(),
                certificateRepository.findByPropertyIdOrderByIssuedAtDesc(id).stream()
                        .map(c -> new CertificateRef(c.getCertificateNo(), c.getIssuedAt(), c.getValidTill()))
                        .toList());
    }

    @Transactional
    public PropertyView create(AuthUser user, PropertyRequest req) {
        user.require(Role.RECORD_KEEPER, Role.CLERK);

        Ward ward = wardRepository.findById(req.wardId()).orElseThrow(() -> ApiException.notFound("Ward"));
        user.checkUlb(ward.getUlbId());
        if (user.restrictedToWards() && !user.wardIds().contains(ward.getId())) {
            throw ApiException.forbidden("Ward " + ward.getWardNo() + " is not assigned to you");
        }

        if (req.ownerName() == null || req.ownerName().isBlank()) {
            throw ApiException.badRequest("Owner name is required");
        }

        Ulb ulb = ulbRepository.findById(ward.getUlbId()).orElseThrow();
        int serial = propertyRepository.maxSerialInWard(ward.getId()) + 1;

        Property p = new Property();
        p.setUlb(ulb);
        p.setWard(ward);
        p.setSerialNo(serial);
        p.setPropertyNo(Property.buildPropertyNo(ulb, ward, serial));
        p.setOwnerName(req.ownerName().trim());
        p.setOwnerMobile(req.ownerMobile());
        p.setOwnerEmail(req.ownerEmail());
        applyDetails(p, req);
        propertyRepository.save(p);

        notificationService.smsAndEmail(p.getOwnerMobile(), p.getOwnerEmail(),
                "Your property at " + p.getAddress() + " is registered with ID " + p.getPropertyNo()
                        + ". Use this ID for tax payment and mutation. - " + ulb.getName(),
                ulb.getId(), "PROPERTY", p.getId());

        return PropertyView.of(p);
    }

    @Transactional
    public PropertyView update(AuthUser user, Long id, PropertyRequest req) {
        user.require(Role.RECORD_KEEPER, Role.CLERK);
        Property p = load(id);
        user.checkUlb(p.getUlb().getId());

        if (!p.getWard().getId().equals(req.wardId())) {
            throw ApiException.badRequest("Ward can't be changed as it is part of the property ID");
        }
        applyDetails(p, req);
        return PropertyView.of(p);
    }

    @Transactional
    public CertificateRef issueNoDues(AuthUser user, Long id) {
        user.require(Role.CLERK, Role.COUNTER, Role.APPROVER);
        Property p = load(id);
        user.checkUlb(p.getUlb().getId());
        NoDuesCertificate cert = noDuesService.issue(p, null);
        return new CertificateRef(cert.getCertificateNo(), cert.getIssuedAt(), cert.getValidTill());
    }

    private void applyDetails(Property p, PropertyRequest req) {
        p.setKhasraNo(req.khasraNo());
        p.setCategory(req.category());
        p.setAddress(req.address().trim());
        p.setPlotArea(nz(req.plotArea()));
        p.setCoveredArea(nz(req.coveredArea()));
        p.setCarpetArea(nz(req.carpetArea()));
        p.setConstructionYear(req.constructionYear());

        if (p.getCategory() == PropertyCategory.VACANT_LAND && p.getCoveredArea().signum() > 0) {
            throw ApiException.badRequest("Vacant land can't have covered area");
        }
        if (p.getCoveredArea().compareTo(p.getPlotArea()) > 0) {
            throw ApiException.badRequest("Covered area is more than plot area");
        }
        if (p.getCarpetArea().compareTo(p.getCoveredArea()) > 0) {
            throw ApiException.badRequest("Carpet area can't be more than covered area");
        }
    }

    private Property load(Long id) {
        return propertyRepository.findDetailed(id).orElseThrow(() -> ApiException.notFound("Property"));
    }

    private void checkCanView(AuthUser user, Property p) {
        if (user.role() == Role.CITIZEN) {
            if (user.mobile() == null || !user.mobile().equals(p.getOwnerMobile())) {
                throw ApiException.forbidden("This property is not registered in your name");
            }
            return;
        }
        user.checkUlb(p.getUlb().getId());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ---------- DTOs ----------

    public record PropertyRequest(
            @NotNull Long wardId,
            String khasraNo,
            @NotNull PropertyCategory category,
            String ownerName,
            String ownerMobile,
            String ownerEmail,
            @NotBlank String address,
            @PositiveOrZero BigDecimal plotArea,
            @PositiveOrZero BigDecimal coveredArea,
            @PositiveOrZero BigDecimal carpetArea,
            Integer constructionYear) {
    }

    public record PropertyView(Long id, String propertyNo, Long ulbId, String ulbName, Long wardId, int wardNo,
                               String wardName, String khasraNo, PropertyCategory category, String ownerName,
                               String address) {
        static PropertyView of(Property p) {
            return new PropertyView(p.getId(), p.getPropertyNo(), p.getUlb().getId(), p.getUlb().getName(),
                    p.getWard().getId(), p.getWard().getWardNo(), p.getWard().getName(), p.getKhasraNo(),
                    p.getCategory(), p.getOwnerName(), p.getAddress());
        }
    }

    public record MutationRef(Long id, String applicationNo, String status, String newOwnerName, LocalDateTime createdAt) {
    }

    public record CertificateRef(String certificateNo, LocalDateTime issuedAt, LocalDate validTill) {
    }

    public record PropertyDetail(PropertyView property, String ownerMobile, String ownerEmail,
                                 BigDecimal plotArea, BigDecimal coveredArea, BigDecimal carpetArea,
                                 Integer constructionYear, LocalDate lastMutationDate, LocalDateTime createdAt,
                                 BillingService.PropertyAccount account,
                                 List<AssessmentService.AssessmentView> assessments,
                                 List<MutationRef> mutations,
                                 List<CertificateRef> certificates) {
    }
}
