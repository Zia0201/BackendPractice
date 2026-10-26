package in.uk.ulb.ptax.mutation;

import in.uk.ulb.ptax.common.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MutationRepository extends JpaRepository<MutationApplication, Long> {

    Optional<MutationApplication> findByApplicationNo(String applicationNo);

    List<MutationApplication> findByUlbIdOrderByCreatedAtDesc(Long ulbId);

    List<MutationApplication> findAllByOrderByCreatedAtDesc();

    List<MutationApplication> findByUlbIdAndStatusAndCurrentRoleOrderByCreatedAt(Long ulbId, MutationStatus status, Role role);

    @Query("""
            select m from MutationApplication m
            where m.filedBy = :userId or m.newOwnerMobile = :mobile
            order by m.createdAt desc
            """)
    List<MutationApplication> findForCitizen(Long userId, String mobile);

    List<MutationApplication> findByPropertyIdOrderByCreatedAtDesc(Long propertyId);

    boolean existsByPropertyIdAndStatus(Long propertyId, MutationStatus status);

    @Query(value = "select nextval('mutation_no_seq')", nativeQuery = true)
    long nextApplicationNumber();
}
