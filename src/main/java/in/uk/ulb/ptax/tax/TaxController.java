package in.uk.ulb.ptax.tax;

import in.uk.ulb.ptax.auth.AuthUser;
import in.uk.ulb.ptax.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TaxController {

    private final AssessmentService assessmentService;

    /** Just runs the formula, nothing is saved. Used by the calculator screen. */
    @PostMapping("/api/tax/calculate")
    public TaxResult calculate(AuthUser user, @RequestParam(required = false) Long ulbId,
                               @Valid @RequestBody TaxInput input) {
        Long ulb = ulbId != null ? ulbId : user.ulbId();
        if (ulb == null) {
            throw ApiException.badRequest("Select a ULB");
        }
        return assessmentService.preview(ulb, input);
    }

    @PostMapping("/api/properties/{propertyId}/assessments")
    public AssessmentService.AssessmentView assess(AuthUser user, @PathVariable Long propertyId,
                                                   @Valid @RequestBody AssessRequest req) {
        return assessmentService.assess(user, propertyId, req.financialYear(), req.input());
    }

    public record AssessRequest(String financialYear, @NotNull @Valid TaxInput input) {
    }
}
