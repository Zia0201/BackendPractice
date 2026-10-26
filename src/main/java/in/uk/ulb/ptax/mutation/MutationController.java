package in.uk.ulb.ptax.mutation;

import in.uk.ulb.ptax.auth.AuthUser;
import in.uk.ulb.ptax.mutation.MutationService.ActionRequest;
import in.uk.ulb.ptax.mutation.MutationService.FileRequest;
import in.uk.ulb.ptax.mutation.MutationService.MutationDetail;
import in.uk.ulb.ptax.mutation.MutationService.MutationSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mutations")
@RequiredArgsConstructor
public class MutationController {

    private final MutationService mutationService;

    @GetMapping
    public List<MutationSummary> list(AuthUser user, @RequestParam(required = false) MutationStatus status) {
        return mutationService.list(user, status);
    }

    @GetMapping("/queue")
    public List<MutationSummary> queue(AuthUser user) {
        return mutationService.myQueue(user);
    }

    @GetMapping("/{id}")
    public MutationDetail get(AuthUser user, @PathVariable Long id) {
        return mutationService.detail(user, id);
    }

    @PostMapping
    public MutationDetail file(AuthUser user, @Valid @RequestBody FileRequest req) {
        return mutationService.file(user, req);
    }

    @PostMapping("/{id}/actions")
    public MutationDetail act(AuthUser user, @PathVariable Long id, @Valid @RequestBody ActionRequest req) {
        return mutationService.act(user, id, req.action(), req.remarks());
    }
}
