package in.uk.ulb.ptax.tax;

import in.uk.ulb.ptax.auth.AuthUser;
import in.uk.ulb.ptax.billing.Demand;
import in.uk.ulb.ptax.billing.DemandRepository;
import in.uk.ulb.ptax.billing.DemandType;
import in.uk.ulb.ptax.common.ApiException;
import in.uk.ulb.ptax.common.FinancialYear;
import in.uk.ulb.ptax.common.Role;
import in.uk.ulb.ptax.notification.NotificationService;
import in.uk.ulb.ptax.property.Property;
import in.uk.ulb.ptax.property.PropertyRepository;
import in.uk.ulb.ptax.ulb.UlbTaxConfig;
import in.uk.ulb.ptax.ulb.UlbTaxConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final TaxCalculator calculator;
    private final UlbTaxConfigRepository configRepository;
    private final PropertyRepository propertyRepository;
    private final TaxAssessmentRepository assessmentRepository;
    private final DemandRepository demandRepository;
    private final NotificationService notificationService;

    public TaxResult preview(Long ulbId, TaxInput input) {
        return calculator.calculate(input, config(ulbId));
    }

    /**
     * Assess a property for a financial year and raise (or revise) the property tax demand.
     * Done by the field inspector on site, or by the clerk from the office.
     */
    @Transactional
    public AssessmentView assess(AuthUser user, Long propertyId, String financialYear, TaxInput input) {
        user.require(Role.FIELD_INSPECTOR, Role.CLERK);

        Property property = propertyRepository.findDetailed(propertyId)
                .orElseThrow(() -> ApiException.notFound("Property"));
        user.checkUlb(property.getUlb().getId());

        if (input.category() != property.getCategory()) {
            throw ApiException.badRequest("Property is registered as " + property.getCategory()
                    + ". Update the property first if the usage has changed.");
        }

        String fy = financialYear == null || financialYear.isBlank() ? FinancialYear.current() : financialYear;
        UlbTaxConfig cfg = config(property.getUlb().getId());
        TaxResult result = calculator.calculate(input, cfg);

        BigDecimal finalTax = result.tax();
        String breakdown = result.breakdownText();
        if (input.method() == TaxMethod.CIRCLE_RATE) {
            BigDecimal previous = assessmentRepository
                    .findByPropertyIdAndFinancialYear(propertyId, FinancialYear.previous(fy))
                    .map(TaxAssessment::getFinalTax)
                    .orElse(null);
            finalTax = calculator.applyTransitionRules(result.tax(), previous, cfg.getRulesEffectiveFrom(),
                    FinancialYear.startYear(fy));
            if (finalTax.compareTo(result.tax()) != 0) {
                breakdown += "\nAdjusted to Rs " + finalTax + " (previous year Rs " + previous
                        + ", 5 year transition rule)";
            }
        }

        TaxAssessment assessment = assessmentRepository.findByPropertyIdAndFinancialYear(propertyId, fy)
                .orElseGet(TaxAssessment::new);
        assessment.setPropertyId(propertyId);
        assessment.setFinancialYear(fy);
        assessment.setMethod(input.method());
        assessment.setAnnualValue(result.annualValue());
        assessment.setComputedTax(result.tax());
        assessment.setFinalTax(finalTax);
        assessment.setBreakdown(breakdown);
        assessment.setAssessedBy(user.id());
        assessment.setAssessedAt(LocalDateTime.now());
        assessmentRepository.save(assessment);

        Demand demand = demandRepository
                .findByPropertyIdAndDemandTypeAndFinancialYear(propertyId, DemandType.PROPERTY_TAX, fy)
                .orElseGet(() -> {
                    Demand d = new Demand();
                    d.setPropertyId(propertyId);
                    d.setDemandType(DemandType.PROPERTY_TAX);
                    d.setFinancialYear(fy);
                    return d;
                });
        if (demand.getPaidAmount().compareTo(finalTax) > 0) {
            throw ApiException.badRequest("Rs " + demand.getPaidAmount() + " is already paid for " + fy
                    + ", which is more than the new assessment. Refund/adjustment has to be done manually.");
        }
        demand.setAmount(finalTax);
        demandRepository.save(demand);

        // keep the register in sync with what was measured on site
        if (input.carpetArea() != null) property.setCarpetArea(input.carpetArea());
        if (input.coveredArea() != null) property.setCoveredArea(input.coveredArea());
        if (input.plotArea() != null) property.setPlotArea(input.plotArea());

        notificationService.sms(property.getOwnerMobile(),
                "Property tax for " + property.getPropertyNo() + " for FY " + fy + " is assessed at Rs " + finalTax
                        + ". Pay online or at the ULB counter. - " + property.getUlb().getName(),
                property.getUlb().getId(), "ASSESSMENT", assessment.getId());

        return AssessmentView.of(assessment);
    }

    @Transactional(readOnly = true)
    public List<AssessmentView> history(Long propertyId) {
        return assessmentRepository.findByPropertyIdOrderByFinancialYearDesc(propertyId).stream()
                .map(AssessmentView::of)
                .toList();
    }

    private UlbTaxConfig config(Long ulbId) {
        return configRepository.findById(ulbId)
                .orElseThrow(() -> ApiException.badRequest("Tax rates are not configured for this ULB yet"));
    }

    public record AssessmentView(Long id, String financialYear, TaxMethod method, BigDecimal annualValue,
                                 BigDecimal computedTax, BigDecimal finalTax, String breakdown,
                                 LocalDateTime assessedAt) {
        static AssessmentView of(TaxAssessment a) {
            return new AssessmentView(a.getId(), a.getFinancialYear(), a.getMethod(), a.getAnnualValue(),
                    a.getComputedTax(), a.getFinalTax(), a.getBreakdown(), a.getAssessedAt());
        }
    }
}
