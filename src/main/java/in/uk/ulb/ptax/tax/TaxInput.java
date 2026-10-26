package in.uk.ulb.ptax.tax;

import in.uk.ulb.ptax.property.PropertyCategory;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Areas are in sq ft. Only the fields relevant to the chosen method/category are needed.
 *
 * @param useCoveredArea residential self assessment can go on covered area (at 80% of the rate)
 *                       when carpet area is not known
 */
public record TaxInput(
        @NotNull TaxMethod method,
        @NotNull PropertyCategory category,
        BigDecimal carpetArea,
        BigDecimal coveredArea,
        BigDecimal plotArea,
        Boolean useCoveredArea,
        List<RentUnit> rentUnits) {

    public boolean onCoveredArea() {
        return Boolean.TRUE.equals(useCoveredArea);
    }

    public record RentUnit(String description, BigDecimal monthlyRent) {
    }
}
