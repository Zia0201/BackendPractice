package in.uk.ulb.ptax.tax;

import in.uk.ulb.ptax.common.ApiException;
import in.uk.ulb.ptax.property.PropertyCategory;
import in.uk.ulb.ptax.ulb.UlbTaxConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxCalculatorTest {

    private final TaxCalculator calculator = new TaxCalculator();
    private UlbTaxConfig cfg;

    @BeforeEach
    void setUp() {
        cfg = new UlbTaxConfig();
        cfg.setResCarpetRate(new BigDecimal("2.00"));
        cfg.setNonresCarpetRate(new BigDecimal("5.00"));
        cfg.setNonresCoefficient(new BigDecimal("1.5"));
        cfg.setNonresMultiplier(BigDecimal.ONE);
        cfg.setPropertyMultiplier(new BigDecimal("1.1"));
        cfg.setArvTaxRate(new BigDecimal("8"));
        cfg.setVacantLandRate(new BigDecimal("0.50"));
        cfg.setCircleRate(new BigDecimal("2000"));
        cfg.setGeneralTaxRate(new BigDecimal("0.1"));
        cfg.setBuildingDepreciation(new BigDecimal("0.8"));
        cfg.setRulesEffectiveFrom(LocalDate.of(2024, 4, 1));
    }

    @Test
    void rentBased_matchesExampleFromCircular() {
        // 2 rooms self occupied @1000/month + 1 room and shop @2000/month, 8% => 2880
        var input = new TaxInput(TaxMethod.RENT_BASED, PropertyCategory.RESIDENTIAL, null, null, null, false,
                List.of(new TaxInput.RentUnit("2 rooms (owner)", new BigDecimal("1000")),
                        new TaxInput.RentUnit("1 room, 1 shop", new BigDecimal("2000"))));

        TaxResult r = calculator.calculate(input, cfg);

        assertEquals(new BigDecimal("36000.00"), r.annualValue());
        assertEquals(new BigDecimal("2880.00"), r.tax());
    }

    @Test
    void residentialOnCarpetArea() {
        var input = new TaxInput(TaxMethod.SELF_ASSESSMENT, PropertyCategory.RESIDENTIAL,
                new BigDecimal("1000"), null, null, false, null);

        TaxResult r = calculator.calculate(input, cfg);

        // 1000 x 2 x 12 = 24000, x 1.1 = 26400, 8% = 2112
        assertEquals(new BigDecimal("26400.00"), r.annualValue());
        assertEquals(new BigDecimal("2112.00"), r.tax());
    }

    @Test
    void residentialOnCoveredAreaUses80PercentRate() {
        var input = new TaxInput(TaxMethod.SELF_ASSESSMENT, PropertyCategory.RESIDENTIAL,
                null, new BigDecimal("1000"), null, true, null);

        TaxResult r = calculator.calculate(input, cfg);

        // 1000 x 1.6 x 12 = 19200, x 1.1 = 21120
        assertEquals(new BigDecimal("21120.00"), r.annualValue());
    }

    @Test
    void nonResidential() {
        var input = new TaxInput(TaxMethod.SELF_ASSESSMENT, PropertyCategory.NON_RESIDENTIAL,
                null, new BigDecimal("500"), null, false, null);

        TaxResult r = calculator.calculate(input, cfg);

        // 500 x 5 x 1.5 x 12 = 45000
        assertEquals(new BigDecimal("45000.00"), r.annualValue());
        assertEquals(new BigDecimal("3600.00"), r.tax());
    }

    @Test
    void circleRateResidential() {
        var input = new TaxInput(TaxMethod.CIRCLE_RATE, PropertyCategory.RESIDENTIAL,
                null, new BigDecimal("600"), new BigDecimal("1000"), false, null);

        TaxResult r = calculator.calculate(input, cfg);

        // land: 400 x 2000 = 800000, building: 600 x 2000 x 0.8 = 960000
        assertEquals(new BigDecimal("1760000.00"), r.annualValue());
        assertEquals(new BigDecimal("1760.00"), r.tax());
    }

    @Test
    void circleRateRejectsCoveredMoreThanPlot() {
        var input = new TaxInput(TaxMethod.CIRCLE_RATE, PropertyCategory.RESIDENTIAL,
                null, new BigDecimal("1200"), new BigDecimal("1000"), false, null);

        assertThrows(ApiException.class, () -> calculator.calculate(input, cfg));
    }

    @Test
    void transitionRules_floorAndCap() {
        LocalDate effective = LocalDate.of(2024, 4, 1);
        BigDecimal previous = new BigDecimal("1000.00");

        // lower than last year -> last year's tax
        assertEquals(previous, calculator.applyTransitionRules(new BigDecimal("800.00"), previous, effective, 2026));
        // more than 5% up -> capped at 5%
        assertEquals(new BigDecimal("1050.00"), calculator.applyTransitionRules(new BigDecimal("1500.00"), previous, effective, 2026));
        // within the band -> as computed
        assertEquals(new BigDecimal("1030.00"), calculator.applyTransitionRules(new BigDecimal("1030.00"), previous, effective, 2026));
        // after 5 years the rules don't apply
        assertEquals(new BigDecimal("1500.00"), calculator.applyTransitionRules(new BigDecimal("1500.00"), previous, effective, 2029));
    }
}
