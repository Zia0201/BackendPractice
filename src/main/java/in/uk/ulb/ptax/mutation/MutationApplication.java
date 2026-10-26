package in.uk.ulb.ptax.mutation;

import in.uk.ulb.ptax.common.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class MutationApplication {

    public static final int SLA_DAYS = 60;
    public static final int SLA_DAYS_DISPUTED = 90;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String applicationNo;
    private Long propertyId;
    private Long ulbId;

    @Enumerated(EnumType.STRING)
    private MutationReason reason;

    private String previousOwner;
    private String newOwnerName;
    private String newOwnerMobile;
    private String newOwnerEmail;
    private String deedNo;
    private LocalDate deedDate;

    @Enumerated(EnumType.STRING)
    private MutationStatus status;

    private Integer currentStep;

    // current_role is a reserved word in postgres
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_role")
    private Role currentRole;

    private boolean disputed;
    private String filedVia;
    private Long filedBy;
    private LocalDate slaDueDate;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isOverdue() {
        return status == MutationStatus.IN_PROGRESS && LocalDate.now().isAfter(slaDueDate);
    }
}
