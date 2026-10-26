package in.uk.ulb.ptax.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxAssessmentRepository extends JpaRepository<TaxAssessment, Long> {

    List<TaxAssessment> findByPropertyIdOrderByFinancialYearDesc(Long propertyId);

    Optional<TaxAssessment> findByPropertyIdAndFinancialYear(Long propertyId, String financialYear);
}
