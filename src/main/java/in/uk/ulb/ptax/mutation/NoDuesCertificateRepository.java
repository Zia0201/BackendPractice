package in.uk.ulb.ptax.mutation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NoDuesCertificateRepository extends JpaRepository<NoDuesCertificate, Long> {

    List<NoDuesCertificate> findByPropertyIdOrderByIssuedAtDesc(Long propertyId);

    @Query(value = "select nextval('certificate_seq')", nativeQuery = true)
    long nextCertificateNumber();
}
