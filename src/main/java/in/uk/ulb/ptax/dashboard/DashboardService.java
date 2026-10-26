package in.uk.ulb.ptax.dashboard;

import in.uk.ulb.ptax.auth.AuthUser;
import in.uk.ulb.ptax.billing.DemandRepository;
import in.uk.ulb.ptax.common.Role;
import in.uk.ulb.ptax.mutation.MutationApplication;
import in.uk.ulb.ptax.mutation.MutationRepository;
import in.uk.ulb.ptax.mutation.MutationService;
import in.uk.ulb.ptax.mutation.MutationStatus;
import in.uk.ulb.ptax.property.Property;
import in.uk.ulb.ptax.property.PropertyRepository;
import in.uk.ulb.ptax.ulb.Ulb;
import in.uk.ulb.ptax.ulb.UlbRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MutationRepository mutationRepository;
    private final MutationService mutationService;
    private final PropertyRepository propertyRepository;
    private final DemandRepository demandRepository;
    private final UlbRepository ulbRepository;

    @Transactional(readOnly = true)
    public Dashboard forUser(AuthUser user) {
        if (user.role() == Role.ADMIN) {
            List<UlbStats> perUlb = ulbRepository.findAllByOrderByNameAsc().stream().map(this::stats).toList();
            return new Dashboard(null, 0, perUlb, List.of());
        }

        UlbStats stats = stats(ulbRepository.findById(user.ulbId()).orElseThrow());
        int queue = mutationService.myQueue(user).size();

        List<MissingRecord> missing = List.of();
        if (user.is(Role.RECORD_KEEPER, Role.CLERK)) {
            List<Property> props = user.restrictedToWards()
                    ? propertyRepository.findMissingMutationRecords(user.ulbId(), user.wardIds())
                    : propertyRepository.findMissingMutationRecords(user.ulbId());
            missing = props.stream()
                    .map(p -> new MissingRecord(p.getId(), p.getPropertyNo(), p.getWard().getName(), p.getOwnerName()))
                    .toList();
        }
        return new Dashboard(stats, queue, List.of(), missing);
    }

    @Transactional(readOnly = true)
    public UlbStats stats(Ulb ulb) {
        List<MutationApplication> apps = mutationRepository.findByUlbIdOrderByCreatedAtDesc(ulb.getId());
        Object[] totals = demandRepository.totals(ulb.getId()).get(0);
        BigDecimal demand = (BigDecimal) totals[0];
        BigDecimal collected = (BigDecimal) totals[1];

        return new UlbStats(
                ulb.getId(),
                ulb.getName(),
                propertyRepository.countByUlbId(ulb.getId()),
                count(apps, MutationStatus.IN_PROGRESS),
                count(apps, MutationStatus.APPROVED),
                count(apps, MutationStatus.REJECTED),
                apps.stream().filter(MutationApplication::isOverdue).count(),
                apps.stream().filter(a -> a.isDisputed() && a.getStatus() == MutationStatus.IN_PROGRESS).count(),
                demand,
                collected,
                demand.subtract(collected));
    }

    private static long count(List<MutationApplication> apps, MutationStatus status) {
        return apps.stream().filter(a -> a.getStatus() == status).count();
    }

    public record Dashboard(UlbStats stats, int pendingWithMe, List<UlbStats> ulbs, List<MissingRecord> missingMutationRecords) {
    }

    public record UlbStats(Long ulbId, String ulbName, long properties, long mutationsInProgress,
                           long mutationsApproved, long mutationsRejected, long overdue, long disputed,
                           BigDecimal totalDemand, BigDecimal collected, BigDecimal outstanding) {
    }

    public record MissingRecord(Long propertyId, String propertyNo, String wardName, String ownerName) {
    }
}
