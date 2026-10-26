package in.uk.ulb.ptax.mutation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MutationActionRepository extends JpaRepository<MutationAction, Long> {

    List<MutationAction> findByMutationIdOrderByActionAt(Long mutationId);
}
