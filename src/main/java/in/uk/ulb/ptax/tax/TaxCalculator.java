package in.uk.ulb.ptax.tax;

import in.uk.ulb.ptax.common.ApiException;
import in.uk.ulb.ptax.property.PropertyCategory;
import in.uk.ulb.ptax.tax.TaxResult.Step;
import in.uk.ulb.ptax.ulb.UlbTaxConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Formulas from "Formula for Determination of Property Tax in Municipal Corporations of Uttarakhand".
 * All rates come from the ULB's {@link UlbTaxConfig}. Tax rates in the config are percentages.
 */
@Component
public class TaxCalculator {

    private static final BigDecimal MONTHS = BigDecimal.valueOf(12);
    private static final BigDecimal COVERED_AREA_FACTOR = new BigDecimal("0.80");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_YEARLY_INCREASE = new BigDecimal("1.05");
    private static final int TRANSITION_YEARS = 5;

    public TaxResult calculate(TaxInput in, UlbTaxConfig cfg) {
        return switch (in.method()) {
            case SELF_ASSESSMENT -> selfAssessment(in, cfg);
            case CIRCLE_RATE -> circleRate(in, cfg);
            case RENT_BASED -> rentBased(in, cfg);
        };
    }

    // ---- A. self assessment ----

    private TaxResult selfAssessment(TaxInput in, UlbTaxConfig cfg) {
        List<Step> steps = new ArrayList<>();
        BigDecimal arv;
        BigDecimal totalArv;

        switch (in.category()) {
            case RESIDENTIAL -> {
                if (in.onCoveredArea()) {
                    BigDecimal area = required(in.coveredArea(), "Covered area");
                    BigDecimal rate = cfg.getResCarpetRate().multiply(COVERED_AREA_FACTOR);
                    arv = money(area.multiply(rate).multiply(MONTHS));
                    steps.add(new Step("Annual rental value",
                            area + " sq ft x (80% of Rs " + cfg.getResCarpetRate() + ") x 12", arv));
                } else {
                    BigDecimal area = required(in.carpetArea(), "Carpet area");
                    arv = money(area.multiply(cfg.getResCarpetRate()).multiply(MONTHS));
                    steps.add(new Step("Annual rental value",
                            area + " sq ft x Rs " + cfg.getResCarpetRate() + " x 12", arv));
                }
                totalArv = money(arv.multiply(cfg.getPropertyMultiplier()));
                steps.add(new Step("Total ARV", arv + " x multiplier " + cfg.getPropertyMultiplier(), totalArv));
            }
            case NON_RESIDENTIAL -> {
                BigDecimal area = required(in.coveredArea(), "Covered area");
                arv = money(area.multiply(cfg.getNonresCarpetRate()).multiply(cfg.getNonresCoefficient()).multiply(MONTHS));
                steps.add(new Step("Annual rental value",
                        area + " sq ft x Rs " + cfg.getNonresCarpetRate() + " x coefficient "
                                + cfg.getNonresCoefficient() + " x 12", arv));
                // The circular says "x monthly rent rates for non-residential premises" here, which would
                // apply the rate twice. Treated as a separate ULB multiplier until UDD clarifies.
                totalArv = money(arv.multiply(cfg.getNonresMultiplier()));
                steps.add(new Step("Total ARV", arv + " x multiplier " + cfg.getNonresMultiplier(), totalArv));
            }
            case VACANT_LAND -> {
                BigDecimal area = required(in.plotArea(), "Total area");
                totalArv = money(area.multiply(cfg.getVacantLandRate()).multiply(MONTHS));
                steps.add(new Step("Annual rental value",
                        area + " sq ft x Rs " + cfg.getVacantLandRate() + " x 12", totalArv));
            }
            default -> throw new IllegalStateException("Unexpected category " + in.category());
        }

        BigDecimal tax = percent(totalArv, cfg.getArvTaxRate());
        steps.add(new Step("Property tax", totalArv + " x " + cfg.getArvTaxRate() + "%", tax));
        return new TaxResult(TaxMethod.SELF_ASSESSMENT, totalArv, tax, steps);
    }

    // ---- B. circle rate ----

    private TaxResult circleRate(TaxInput in, UlbTaxConfig cfg) {
        List<Step> steps = new ArrayList<>();
        BigDecimal circle = cfg.getCircleRate();
        BigDecimal plot = required(in.plotArea(), "Plot area");
        BigDecimal covered = in.category() == PropertyCategory.VACANT_LAND
                ? BigDecimal.ZERO
                : required(in.coveredArea(), "Covered area");

        if (covered.compareTo(plot) > 0) {
            throw ApiException.badRequest("Covered area cannot be more than plot area");
        }

        BigDecimal openArea = plot.subtract(covered);
        BigDecimal landValue = money(openArea.multiply(circle));
        steps.add(new Step("Annual value of land", openArea + " sq ft (not covered) x Rs " + circle, landValue));

        BigDecimal buildingValue = BigDecimal.ZERO;
        if (in.category() == PropertyCategory.RESIDENTIAL) {
            buildingValue = money(covered.multiply(circle).multiply(cfg.getBuildingDepreciation()));
            steps.add(new Step("Annual value of building",
                    covered + " sq ft x Rs " + circle + " x depreciation " + cfg.getBuildingDepreciation(), buildingValue));
        } else if (in.category() == PropertyCategory.NON_RESIDENTIAL) {
            buildingValue = money(covered.multiply(circle));
            steps.add(new Step("Annual value of building", covered + " sq ft x Rs " + circle, buildingValue));
        }

        BigDecimal total = landValue.add(buildingValue);
        steps.add(new Step("Total annual value", landValue + " + " + buildingValue, total));

        // For vacant land the circular says tax = annual value, with no rate. That gives absurd figures
        // (the full land value as tax every year), so we apply the general tax rate here as well.
        BigDecimal tax = percent(total, cfg.getGeneralTaxRate());
        steps.add(new Step("Property tax", total + " x " + cfg.getGeneralTaxRate() + "%", tax));

        return new TaxResult(TaxMethod.CIRCLE_RATE, total, tax, steps);
    }

    // ---- C. rent based ----

    private TaxResult rentBased(TaxInput in, UlbTaxConfig cfg) {
        if (in.rentUnits() == null || in.rentUnits().isEmpty()) {
            throw ApiException.badRequest("Add at least one room / shop with its monthly rent");
        }

        List<Step> steps = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int i = 1;
        for (TaxInput.RentUnit unit : in.rentUnits()) {
            BigDecimal rent = required(unit.monthlyRent(), "Monthly rent");
            BigDecimal yearly = money(rent.multiply(MONTHS));
            String label = unit.description() == null || unit.description().isBlank() ? "Unit " + i : unit.description();
            steps.add(new Step(label, "Rs " + rent + " x 12", yearly));
            total = total.add(yearly);
            i++;
        }
        steps.add(new Step("Annual rental value", "sum of above", total));

        BigDecimal tax = percent(total, cfg.getArvTaxRate());
        steps.add(new Step("Property tax", total + " x " + cfg.getArvTaxRate() + "%", tax));
        return new TaxResult(TaxMethod.RENT_BASED, total, tax, steps);
    }

    /**
     * Circle rate conditions 1 and 2: for the first five years after the rules come into force the
     * tax can't fall below last year's and can't rise more than 5% over it.
     *
     * @return the tax to actually levy
     */
    public BigDecimal applyTransitionRules(BigDecimal computed, BigDecimal previousYearTax,
                                           LocalDate rulesEffectiveFrom, int assessmentStartYear) {
        if (previousYearTax == null || previousYearTax.signum() == 0) {
            return computed;
        }
        int effectiveFy = rulesEffectiveFrom.getMonthValue() >= 4
                ? rulesEffectiveFrom.getYear()
                : rulesEffectiveFrom.getYear() - 1;
        if (assessmentStartYear >= effectiveFy + TRANSITION_YEARS) {
            return computed;
        }

        if (computed.compareTo(previousYearTax) < 0) {
            return previousYearTax;
        }
        BigDecimal cap = money(previousYearTax.multiply(MAX_YEARLY_INCREASE));
        return computed.min(cap);
    }

    private static BigDecimal required(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw ApiException.badRequest(name + " is required");
        }
        return value;
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).divide(HUNDRED, 0, RoundingMode.HALF_UP).setScale(2);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
