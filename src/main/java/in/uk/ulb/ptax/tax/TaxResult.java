package in.uk.ulb.ptax.tax;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public record TaxResult(TaxMethod method, BigDecimal annualValue, BigDecimal tax, List<Step> steps) {

    /** One line of the working, shown to the user so they can check the numbers. */
    public record Step(String label, String formula, BigDecimal value) {
    }

    public String breakdownText() {
        return steps.stream()
                .map(s -> s.label() + ": " + s.formula() + " = " + s.value())
                .collect(Collectors.joining("\n"));
    }
}
