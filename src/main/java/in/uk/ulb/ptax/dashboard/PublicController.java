package in.uk.ulb.ptax.dashboard;

import in.uk.ulb.ptax.billing.DemandRepository;
import in.uk.ulb.ptax.common.ApiException;
import in.uk.ulb.ptax.common.Masking;
import in.uk.ulb.ptax.mutation.MutationApplication;
import in.uk.ulb.ptax.mutation.MutationRepository;
import in.uk.ulb.ptax.mutation.NoDuesCertificate;
import in.uk.ulb.ptax.mutation.NoDuesCertificateRepository;
import in.uk.ulb.ptax.mutation.NoDuesService.NoDuesStatus;
import in.uk.ulb.ptax.property.Property;
import in.uk.ulb.ptax.property.PropertyCategory;
import in.uk.ulb.ptax.property.PropertyRepository;
import in.uk.ulb.ptax.ulb.Ulb;
import in.uk.ulb.ptax.ulb.UlbRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * No login needed. Phone numbers are always masked here.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final UlbRepository ulbRepository;
    private final PropertyRepository propertyRepository;
    private final MutationRepository mutationRepository;
    private final DemandRepository demandRepository;
    private final NoDuesCertificateRepository certificateRepository;
    private final DashboardService dashboardService;

    @GetMapping("/ulbs")
    public List<Ulb> ulbs() {
        return ulbRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/ulbs/{ulbId}/summary")
    public DashboardService.UlbStats summary(@PathVariable Long ulbId) {
        return dashboardService.stats(ulbRepository.findById(ulbId).orElseThrow(() -> ApiException.notFound("ULB")));
    }

    @GetMapping("/properties")
    @Transactional(readOnly = true)
    public List<PublicProperty> search(@RequestParam Long ulbId, @RequestParam String q) {
        String term = q.trim().toLowerCase();
        if (term.length() < 3) {
            throw ApiException.badRequest("Type at least 3 characters to search");
        }
        return propertyRepository.search(ulbId, null, term, PageRequest.of(0, 50)).stream()
                .map(p -> new PublicProperty(p.getPropertyNo(), p.getWard().getName(), p.getCategory(),
                        p.getOwnerName(), Masking.mobile(p.getOwnerMobile()), p.getAddress(),
                        demandRepository.outstandingFor(p.getId())))
                .toList();
    }

    @GetMapping("/mutations/{applicationNo}")
    public MutationTrack track(@PathVariable String applicationNo) {
        MutationApplication m = mutationRepository.findByApplicationNo(applicationNo.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Application"));
        return new MutationTrack(m.getApplicationNo(), m.getStatus().name(),
                m.getCurrentRole() == null ? null : m.getCurrentRole().name(),
                m.isDisputed(), m.getSlaDueDate(), m.isOverdue(), Masking.mobile(m.getNewOwnerMobile()));
    }

    /** For the registration department to check before registering a sale deed. */
    @GetMapping("/no-dues/{propertyNo}")
    @Transactional(readOnly = true)
    public NoDuesStatus noDues(@PathVariable String propertyNo) {
        Property p = propertyRepository.findByPropertyNo(propertyNo.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Property"));
        BigDecimal outstanding = demandRepository.outstandingFor(p.getId());
        NoDuesCertificate latest = certificateRepository.findByPropertyIdOrderByIssuedAtDesc(p.getId()).stream()
                .findFirst().orElse(null);
        return new NoDuesStatus(p.getPropertyNo(), p.getOwnerName(), outstanding, outstanding.signum() == 0,
                latest == null ? null : latest.getCertificateNo(),
                latest == null ? null : latest.getIssuedAt());
    }

    public record PublicProperty(String propertyNo, String wardName, PropertyCategory category, String ownerName,
                                 String ownerMobile, String address, BigDecimal outstanding) {
    }

    public record MutationTrack(String applicationNo, String status, String pendingWith, boolean disputed,
                                LocalDate slaDueDate, boolean overdue, String applicantMobile) {
    }
}
