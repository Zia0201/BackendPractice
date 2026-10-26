package in.uk.ulb.ptax.ulb;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, Long> {

    List<WorkflowStep> findByUlbIdOrderByStepOrder(Long ulbId);

    @Modifying
    @Query("delete from WorkflowStep w where w.ulbId = :ulbId")
    void deleteAllForUlb(Long ulbId);
}
