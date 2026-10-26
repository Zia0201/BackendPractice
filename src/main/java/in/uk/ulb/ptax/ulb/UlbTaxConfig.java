package in.uk.ulb.ptax.ulb;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class UlbTaxConfig {

    @Id
    private Long ulbId;

    // self assessment (ARV)
    private BigDecimal resCarpetRate;
    private BigDecimal nonresCarpetRate;
    private BigDecimal nonresCoefficient;
    private BigDecimal nonresMultiplier;
    private BigDecimal propertyMultiplier;
    private BigDecimal arvTaxRate;
    private BigDecimal vacantLandRate;

    // circle rate method
    private BigDecimal circleRate;
    private BigDecimal generalTaxRate;
    private BigDecimal buildingDepreciation;

    private BigDecimal mutationFee;
    private boolean mutationPaymentRequired;
    private LocalDate rulesEffectiveFrom;
}
