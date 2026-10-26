package in.uk.ulb.ptax.mutation;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class NoDuesCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String certificateNo;
    private Long propertyId;
    private Long mutationId;
    private LocalDateTime issuedAt;
    private LocalDate validTill;

    @PrePersist
    void onCreate() {
        issuedAt = LocalDateTime.now();
    }
}
