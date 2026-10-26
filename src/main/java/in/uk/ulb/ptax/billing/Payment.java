package in.uk.ulb.ptax.billing;

import jakarta.persistence.Entity;
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
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long demandId;
    private String receiptNo;
    private BigDecimal amount;
    private String mode;
    private Long collectedBy;
    private LocalDateTime paidAt;

    @PrePersist
    void onCreate() {
        paidAt = LocalDateTime.now();
    }
}
