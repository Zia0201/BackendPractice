package in.uk.ulb.ptax.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DemandRepository extends JpaRepository<Demand, Long> {

    List<Demand> findByPropertyIdOrderByCreatedAtDesc(Long propertyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Demand d where d.id = :id")
    Optional<Demand> findForUpdate(Long id);

    Optional<Demand> findByPropertyIdAndDemandTypeAndFinancialYear(Long propertyId, DemandType type, String financialYear);

    Optional<Demand> findByMutationId(Long mutationId);

    @Query("select coalesce(sum(d.amount - d.paidAmount), 0) from Demand d where d.propertyId = :propertyId")
    BigDecimal outstandingFor(Long propertyId);

    @Query("""
            select coalesce(sum(d.amount - d.paidAmount), 0) from Demand d
            where d.propertyId = :propertyId and d.demandType = :type
            """)
    BigDecimal outstandingFor(Long propertyId, DemandType type);

    @Query("""
            select coalesce(sum(d.amount), 0), coalesce(sum(d.paidAmount), 0)
            from Demand d, Property p
            where p.id = d.propertyId and (:ulbId is null or p.ulb.id = :ulbId)
            """)
    List<Object[]> totals(Long ulbId);
}
