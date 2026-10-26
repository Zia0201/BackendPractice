package in.uk.ulb.ptax.ulb;

import in.uk.ulb.ptax.common.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ulb_workflow_step")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ulbId;
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    private Role role;

    public WorkflowStep(Long ulbId, int stepOrder, Role role) {
        this.ulbId = ulbId;
        this.stepOrder = stepOrder;
        this.role = role;
    }
}
