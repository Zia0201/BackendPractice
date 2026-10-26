package in.uk.ulb.ptax.billing;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Demand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long propertyId;

    @Enumerated(EnumType.STRING)
    private DemandType demandType;

    private String financialYear;
    private Long mutationId;

    private BigDecimal amount;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public BigDecimal getBalance() {
        return amount.subtract(paidAmount).max(BigDecimal.ZERO);
    }

    public boolean isCleared() {
        return getBalance().signum() == 0;
    }
}
