package in.uk.ulb.ptax.tax;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class TaxAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long propertyId;
    private String financialYear;

    @Enumerated(EnumType.STRING)
    private TaxMethod method;

    private BigDecimal annualValue;
    private BigDecimal computedTax;
    private BigDecimal finalTax;
    private String breakdown;
    private Long assessedBy;
    private LocalDateTime assessedAt;
}
