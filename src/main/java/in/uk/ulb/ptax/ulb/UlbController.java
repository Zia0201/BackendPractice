package in.uk.ulb.ptax.ulb;

import in.uk.ulb.ptax.auth.AuthUser;
import in.uk.ulb.ptax.common.ApiException;
import in.uk.ulb.ptax.common.Role;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/ulbs")
@RequiredArgsConstructor
public class UlbController {

    private final UlbRepository ulbRepository;
    private final WardRepository wardRepository;
    private final UlbTaxConfigRepository configRepository;
    private final WorkflowStepRepository workflowRepository;

    @GetMapping
    public List<Ulb> list(AuthUser user) {
        if (user.role().isUlbStaff()) {
            return ulbRepository.findById(user.ulbId()).map(List::of).orElse(List.of());
        }
        return ulbRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/{ulbId}/wards")
    public List<Ward> wards(@PathVariable Long ulbId) {
        return wardRepository.findByUlbIdOrderByWardNo(ulbId);
    }

    @GetMapping("/{ulbId}/config")
    public UlbTaxConfig config(@PathVariable Long ulbId) {
        return configRepository.findById(ulbId).orElseThrow(() -> ApiException.notFound("Tax config"));
    }

    @PutMapping("/{ulbId}/config")
    @Transactional
    public UlbTaxConfig updateConfig(AuthUser user, @PathVariable Long ulbId, @RequestBody UlbTaxConfig body) {
        user.require(Role.ADMIN);
        if (!ulbRepository.existsById(ulbId)) {
            throw ApiException.notFound("ULB");
        }
        body.setUlbId(ulbId);
        return configRepository.save(body);
    }

    @GetMapping("/{ulbId}/workflow")
    public List<WorkflowStep> workflow(@PathVariable Long ulbId) {
        return workflowRepository.findByUlbIdOrderByStepOrder(ulbId);
    }

    /**
     * Replace the approval chain. Applications already in progress keep their current step number
     * and continue on the new chain from there.
     */
    @PutMapping("/{ulbId}/workflow")
    @Transactional
    public List<WorkflowStep> updateWorkflow(AuthUser user, @PathVariable Long ulbId,
                                             @RequestBody @NotEmpty List<Role> roles) {
        user.require(Role.ADMIN);
        if (roles == null || roles.isEmpty()) {
            throw ApiException.badRequest("Workflow needs at least one step");
        }
        if (roles.contains(Role.ADMIN) || roles.contains(Role.CITIZEN) || roles.contains(Role.COUNTER)) {
            throw ApiException.badRequest("Only ULB officers can be part of the approval chain");
        }
        if (new HashSet<>(roles).size() != roles.size()) {
            throw ApiException.badRequest("A role can appear only once in the chain");
        }

        workflowRepository.deleteAllForUlb(ulbId);
        workflowRepository.flush();
        for (int i = 0; i < roles.size(); i++) {
            workflowRepository.save(new WorkflowStep(ulbId, i + 1, roles.get(i)));
        }
        return workflowRepository.findByUlbIdOrderByStepOrder(ulbId);
    }
}
