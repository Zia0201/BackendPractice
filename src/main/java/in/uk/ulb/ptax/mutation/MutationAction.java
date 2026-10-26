package in.uk.ulb.ptax.mutation;

import in.uk.ulb.ptax.common.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class MutationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long mutationId;

    @Enumerated(EnumType.STRING)
    private Role stepRole;

    @Enumerated(EnumType.STRING)
    private ActionType action;

    private String remarks;
    private Long actionBy;
    private LocalDateTime actionAt;

    @PrePersist
    void onCreate() {
        actionAt = LocalDateTime.now();
    }
}
