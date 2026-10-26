package in.uk.ulb.ptax.mutation;

import in.uk.ulb.ptax.auth.AppUser;
import in.uk.ulb.ptax.auth.AppUserRepository;
import in.uk.ulb.ptax.auth.AuthUser;
import in.uk.ulb.ptax.billing.Demand;
import in.uk.ulb.ptax.billing.DemandRepository;
import in.uk.ulb.ptax.billing.DemandType;
import in.uk.ulb.ptax.common.ApiException;
import in.uk.ulb.ptax.common.Role;
import in.uk.ulb.ptax.notification.NotificationService;
import in.uk.ulb.ptax.property.Property;
import in.uk.ulb.ptax.property.PropertyRepository;
import in.uk.ulb.ptax.ulb.UlbTaxConfig;
import in.uk.ulb.ptax.ulb.UlbTaxConfigRepository;
import in.uk.ulb.ptax.ulb.WorkflowStep;
import in.uk.ulb.ptax.ulb.WorkflowStepRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MutationService {

    private final MutationRepository mutationRepository;
    private final MutationActionRepository actionRepository;
    private final PropertyRepository propertyRepository;
    private final WorkflowStepRepository workflowRepository;
    private final UlbTaxConfigRepository configRepository;
    private final DemandRepository demandRepository;
    private final AppUserRepository userRepository;
    private final NoDuesService noDuesService;
    private final NotificationService notificationService;

    // ---------- filing ----------

    @Transactional
    public MutationDetail file(AuthUser user, FileRequest req) {
        user.require(Role.CITIZEN, Role.COUNTER);

        Property property = propertyRepository.findByPropertyNo(req.propertyNo().trim().toUpperCase())
                .orElseThrow(() -> ApiException.badRequest("No property found with ID " + req.propertyNo()));
        Long ulbId = property.getUlb().getId();
        if (user.role() == Role.COUNTER) {
            user.checkUlb(ulbId);
        }

        if (mutationRepository.existsByPropertyIdAndStatus(property.getId(), MutationStatus.IN_PROGRESS)) {
            throw ApiException.badRequest("A mutation application is already in progress for this property");
        }

        List<WorkflowStep> steps = workflowRepository.findByUlbIdOrderByStepOrder(ulbId);
        if (steps.isEmpty()) {
            throw ApiException.badRequest("Mutation workflow is not configured for " + property.getUlb().getName());
        }
        WorkflowStep first = steps.get(0);

        MutationApplication app = new MutationApplication();
        app.setApplicationNo(String.format("MUT-%s-%d-%04d", property.getUlb().getCode(), LocalDate.now().getYear(),
                mutationRepository.nextApplicationNumber()));
        app.setPropertyId(property.getId());
        app.setUlbId(ulbId);
        app.setReason(req.reason());
        app.setPreviousOwner(property.getOwnerName());
        app.setNewOwnerName(req.newOwnerName().trim());
        app.setNewOwnerMobile(req.newOwnerMobile());
        app.setNewOwnerEmail(req.newOwnerEmail());
        app.setDeedNo(req.deedNo());
        app.setDeedDate(req.deedDate());
        app.setStatus(MutationStatus.IN_PROGRESS);
        app.setCurrentStep(first.getStepOrder());
        app.setCurrentRole(first.getRole());
        app.setFiledVia(user.role() == Role.COUNTER ? "COUNTER" : "CITIZEN");
        app.setFiledBy(user.id());
        app.setSlaDueDate(LocalDate.now().plusDays(MutationApplication.SLA_DAYS));
        mutationRepository.save(app);

        addAction(app, null, ActionType.SUBMITTED,
                user.role() == Role.COUNTER ? "Filed at counter by " + user.fullName() : "Filed online", user);

        // payment step is optional per ULB
        UlbTaxConfig cfg = configRepository.findById(ulbId).orElse(null);
        if (cfg != null && cfg.isMutationPaymentRequired() && cfg.getMutationFee().signum() > 0) {
            Demand fee = new Demand();
            fee.setPropertyId(property.getId());
            fee.setDemandType(DemandType.MUTATION_FEE);
            fee.setMutationId(app.getId());
            fee.setAmount(cfg.getMutationFee());
            demandRepository.save(fee);
        }

        notificationService.smsAndEmail(app.getNewOwnerMobile(), app.getNewOwnerEmail(),
                "Your mutation application " + app.getApplicationNo() + " for property " + property.getPropertyNo()
                        + " is received. Expected disposal by " + app.getSlaDueDate() + ". - " + property.getUlb().getName(),
                ulbId, "MUTATION", app.getId());

        // Tell the recorded owner too. Cheapest guard we have against fraudulent mutation.
        notificationService.sms(property.getOwnerMobile(),
                "A mutation application " + app.getApplicationNo() + " has been filed on your property "
                        + property.getPropertyNo() + " in favour of " + app.getNewOwnerName()
                        + ". If you have not sold/transferred it, contact the ULB office. - " + property.getUlb().getName(),
                ulbId, "MUTATION", app.getId());

        return detail(user, app.getId());
    }

    // ---------- lists ----------

    @Transactional(readOnly = true)
    public List<MutationSummary> myQueue(AuthUser user) {
        if (!user.role().isUlbStaff() || user.role() == Role.COUNTER) {
            return List.of();
        }
        List<MutationApplication> apps = mutationRepository
                .findByUlbIdAndStatusAndCurrentRoleOrderByCreatedAt(user.ulbId(), MutationStatus.IN_PROGRESS, user.role());
        return summarise(apps).stream()
                .filter(s -> !user.restrictedToWards() || user.wardIds().contains(s.wardId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MutationSummary> list(AuthUser user, MutationStatus status) {
        List<MutationApplication> apps = switch (user.role()) {
            case ADMIN -> mutationRepository.findAllByOrderByCreatedAtDesc();
            case CITIZEN -> mutationRepository.findForCitizen(user.id(), user.mobile());
            default -> mutationRepository.findByUlbIdOrderByCreatedAtDesc(user.ulbId());
        };
        if (status != null) {
            apps = apps.stream().filter(a -> a.getStatus() == status).toList();
        }
        return summarise(apps);
    }

    // ---------- detail ----------

    @Transactional(readOnly = true)
    public MutationDetail detail(AuthUser user, Long id) {
        MutationApplication app = get(id);
        checkCanView(user, app);

        Property property = propertyRepository.findDetailed(app.getPropertyId()).orElseThrow();
        List<WorkflowStep> steps = workflowRepository.findByUlbIdOrderByStepOrder(app.getUlbId());
        List<MutationAction> actions = actionRepository.findByMutationIdOrderByActionAt(id);

        Map<Long, String> names = userRepository.findAllById(
                        actions.stream().map(MutationAction::getActionBy).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));

        Demand fee = demandRepository.findByMutationId(id).orElse(null);
        // fee is shown separately, so only tax here
        BigDecimal outstanding = demandRepository.outstandingFor(property.getId(), DemandType.PROPERTY_TAX);

        return new MutationDetail(
                app.getId(), app.getApplicationNo(), app.getStatus(), app.getReason(),
                property.getId(), property.getPropertyNo(), property.getAddress(), property.getWard().getName(),
                property.getUlb().getName(), property.getKhasraNo(),
                app.getPreviousOwner(), app.getNewOwnerName(), app.getNewOwnerMobile(), app.getNewOwnerEmail(),
                app.getDeedNo(), app.getDeedDate(), app.getFiledVia(),
                app.getCurrentStep(), app.getCurrentRole(), app.isDisputed(), app.getSlaDueDate(), app.isOverdue(),
                app.getCreatedAt(), app.getClosedAt(),
                steps.stream().map(s -> new StepView(s.getStepOrder(), s.getRole())).toList(),
                actions.stream().map(a -> new ActionView(a.getAction(), a.getStepRole(), a.getRemarks(),
                        names.getOrDefault(a.getActionBy(), "-"), a.getActionAt())).toList(),
                fee == null ? null : fee.getId(),
                fee == null ? BigDecimal.ZERO : fee.getBalance(),
                outstanding,
                allowedActions(user, app, steps));
    }

    // ---------- workflow actions ----------

    @Transactional
    public MutationDetail act(AuthUser user, Long id, ActionType action, String remarks) {
        MutationApplication app = get(id);
        user.checkUlb(app.getUlbId());

        if (app.getStatus() != MutationStatus.IN_PROGRESS) {
            throw ApiException.badRequest("Application is already " + app.getStatus());
        }

        List<WorkflowStep> steps = workflowRepository.findByUlbIdOrderByStepOrder(app.getUlbId());
        if (!allowedActions(user, app, steps).contains(action)) {
            throw ApiException.forbidden(action + " is not allowed for you on this application right now");
        }
        if (action != ActionType.FORWARDED && action != ActionType.APPROVED && (remarks == null || remarks.isBlank())) {
            throw ApiException.badRequest("Remarks are required");
        }

        Property property = propertyRepository.findDetailed(app.getPropertyId()).orElseThrow();
        Role actingStep = app.getCurrentRole();

        switch (action) {
            case FORWARDED -> {
                WorkflowStep next = nextStep(steps, app.getCurrentStep())
                        .orElseThrow(() -> ApiException.badRequest("This is the last step, approve or reject instead"));
                app.setCurrentStep(next.getStepOrder());
                app.setCurrentRole(next.getRole());
            }
            case SENT_BACK -> {
                WorkflowStep prev = previousStep(steps, app.getCurrentStep())
                        .orElseThrow(() -> ApiException.badRequest("Already at the first step"));
                app.setCurrentStep(prev.getStepOrder());
                app.setCurrentRole(prev.getRole());
            }
            case MARKED_DISPUTED -> {
                app.setDisputed(true);
                app.setSlaDueDate(app.getCreatedAt().toLocalDate().plusDays(MutationApplication.SLA_DAYS_DISPUTED));
                notifyApplicant(app, property, "Your mutation application " + app.getApplicationNo()
                        + " has been marked as disputed. Revised disposal date " + app.getSlaDueDate() + ".");
            }
            case REJECTED -> {
                close(app, MutationStatus.REJECTED);
                notifyApplicant(app, property, "Your mutation application " + app.getApplicationNo()
                        + " has been rejected. Reason: " + remarks);
            }
            case APPROVED -> approve(app, property);
            default -> throw ApiException.badRequest("Unsupported action " + action);
        }

        addAction(app, actingStep, action, remarks, user);
        return detail(user, id);
    }

    private void approve(MutationApplication app, Property property) {
        // All dues have to be clear before mutation
        BigDecimal taxDue = demandRepository.outstandingFor(property.getId(), DemandType.PROPERTY_TAX);
        BigDecimal feeDue = demandRepository.outstandingFor(property.getId(), DemandType.MUTATION_FEE);
        if (taxDue.signum() > 0) {
            throw ApiException.badRequest("Property tax of Rs " + taxDue + " is pending. Dues must be cleared before approval.");
        }
        if (feeDue.signum() > 0) {
            throw ApiException.badRequest("Mutation fee of Rs " + feeDue + " is not paid yet");
        }

        property.setOwnerName(app.getNewOwnerName());
        property.setOwnerMobile(app.getNewOwnerMobile());
        property.setOwnerEmail(app.getNewOwnerEmail());
        property.setLastMutationDate(LocalDate.now());
        close(app, MutationStatus.APPROVED);

        notifyApplicant(app, property, "Mutation " + app.getApplicationNo() + " approved. Property " + property.getPropertyNo()
                + " is now recorded in your name.");

        noDuesService.issue(property, app.getId());
    }

    // ---------- helpers ----------

    /**
     * What the current user can do on this application. The frontend shows buttons from this list.
     */
    private List<ActionType> allowedActions(AuthUser user, MutationApplication app, List<WorkflowStep> steps) {
        List<ActionType> allowed = new ArrayList<>();
        if (app.getStatus() != MutationStatus.IN_PROGRESS || !user.role().isUlbStaff()
                || !user.ulbId().equals(app.getUlbId())) {
            return allowed;
        }

        if (user.role() != Role.COUNTER && !app.isDisputed()) {
            allowed.add(ActionType.MARKED_DISPUTED);
        }
        if (user.role() != app.getCurrentRole()) {
            return allowed;
        }

        boolean last = nextStep(steps, app.getCurrentStep()).isEmpty();
        boolean first = previousStep(steps, app.getCurrentStep()).isEmpty();
        allowed.add(last ? ActionType.APPROVED : ActionType.FORWARDED);
        if (!first) {
            allowed.add(ActionType.SENT_BACK);
        }
        allowed.add(ActionType.REJECTED);
        return allowed;
    }

    private Optional<WorkflowStep> nextStep(List<WorkflowStep> steps, int current) {
        return steps.stream().filter(s -> s.getStepOrder() > current).findFirst();
    }

    private Optional<WorkflowStep> previousStep(List<WorkflowStep> steps, int current) {
        return steps.stream().filter(s -> s.getStepOrder() < current).reduce((a, b) -> b);
    }

    private void close(MutationApplication app, MutationStatus status) {
        app.setStatus(status);
        app.setCurrentRole(null);
        app.setCurrentStep(null);
        app.setClosedAt(LocalDateTime.now());
    }

    private void notifyApplicant(MutationApplication app, Property property, String text) {
        notificationService.smsAndEmail(app.getNewOwnerMobile(), app.getNewOwnerEmail(),
                text + " - " + property.getUlb().getName(), app.getUlbId(), "MUTATION", app.getId());
    }

    private void addAction(MutationApplication app, Role stepRole, ActionType type, String remarks, AuthUser by) {
        MutationAction a = new MutationAction();
        a.setMutationId(app.getId());
        a.setStepRole(stepRole);
        a.setAction(type);
        a.setRemarks(remarks);
        a.setActionBy(by.id());
        actionRepository.save(a);
    }

    private MutationApplication get(Long id) {
        return mutationRepository.findById(id).orElseThrow(() -> ApiException.notFound("Mutation application"));
    }

    private void checkCanView(AuthUser user, MutationApplication app) {
        if (user.role() == Role.CITIZEN) {
            boolean mine = user.id().equals(app.getFiledBy()) || app.getNewOwnerMobile().equals(user.mobile());
            if (!mine) {
                throw ApiException.forbidden("You can only view your own applications");
            }
            return;
        }
        user.checkUlb(app.getUlbId());
    }

    private List<MutationSummary> summarise(List<MutationApplication> apps) {
        if (apps.isEmpty()) {
            return List.of();
        }
        Map<Long, Property> props = propertyRepository.findAllById(apps.stream().map(MutationApplication::getPropertyId).toList())
                .stream().collect(Collectors.toMap(Property::getId, Function.identity()));
        return apps.stream().map(a -> {
            Property p = props.get(a.getPropertyId());
            return new MutationSummary(a.getId(), a.getApplicationNo(), p.getPropertyNo(), p.getWard().getId(),
                    p.getWard().getName(), a.getPreviousOwner(), a.getNewOwnerName(), a.getReason(), a.getStatus(),
                    a.getCurrentRole(), a.isDisputed(), a.getSlaDueDate(), a.isOverdue(), a.getCreatedAt());
        }).toList();
    }

    // ---------- DTOs ----------

    public record FileRequest(
            @NotBlank String propertyNo,
            @NotNull MutationReason reason,
            @NotBlank String newOwnerName,
            @NotBlank @Pattern(regexp = "[6-9]\\d{9}", message = "should be a 10 digit mobile number") String newOwnerMobile,
            String newOwnerEmail,
            String deedNo,
            LocalDate deedDate) {
    }

    public record ActionRequest(@NotNull ActionType action, String remarks) {
    }

    public record MutationSummary(Long id, String applicationNo, String propertyNo, Long wardId, String wardName,
                                  String previousOwner, String newOwnerName, MutationReason reason,
                                  MutationStatus status, Role currentRole, boolean disputed,
                                  LocalDate slaDueDate, boolean overdue, LocalDateTime createdAt) {
    }

    public record StepView(int order, Role role) {
    }

    public record ActionView(ActionType action, Role stepRole, String remarks, String by, LocalDateTime at) {
    }

    public record MutationDetail(Long id, String applicationNo, MutationStatus status, MutationReason reason,
                                 Long propertyId, String propertyNo, String address, String wardName, String ulbName,
                                 String khasraNo, String previousOwner, String newOwnerName, String newOwnerMobile,
                                 String newOwnerEmail, String deedNo, LocalDate deedDate, String filedVia,
                                 Integer currentStep, Role currentRole, boolean disputed, LocalDate slaDueDate,
                                 boolean overdue, LocalDateTime createdAt, LocalDateTime closedAt,
                                 List<StepView> workflow, List<ActionView> history,
                                 Long feeDemandId, BigDecimal feeBalance, BigDecimal propertyOutstanding,
                                 List<ActionType> allowedActions) {
    }
}
