package in.uk.ulb.ptax.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByDemandIdInOrderByPaidAtDesc(Collection<Long> demandIds);

    @Query(value = "select nextval('receipt_seq')", nativeQuery = true)
    long nextReceiptNumber();
}
