package in.uk.ulb.ptax.property;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    @Query("select p from Property p join fetch p.ulb join fetch p.ward where p.propertyNo = :propertyNo")
    Optional<Property> findByPropertyNo(String propertyNo);

    @Query("select p from Property p join fetch p.ulb join fetch p.ward where p.id = :id")
    Optional<Property> findDetailed(Long id);

    @Query("""
            select p from Property p join fetch p.ulb join fetch p.ward w
            where (:ulbId is null or p.ulb.id = :ulbId)
              and (:wardId is null or w.id = :wardId)
              and (:q = '' or lower(p.propertyNo) like concat('%', :q, '%')
                           or lower(p.ownerName) like concat('%', :q, '%')
                           or lower(coalesce(p.khasraNo, '')) like concat('%', :q, '%'))
            order by p.propertyNo
            """)
    List<Property> search(Long ulbId, Long wardId, String q, Pageable pageable);

    @Query("select p from Property p join fetch p.ulb join fetch p.ward where p.ownerMobile = :mobile order by p.propertyNo")
    List<Property> findOwnedBy(String mobile);

    @Query("select coalesce(max(p.serialNo), 0) from Property p where p.ward.id = :wardId")
    int maxSerialInWard(Long wardId);

    /** Properties that have never gone through mutation in our system - the record clerk's "missing" list. */
    @Query("""
            select p from Property p join fetch p.ward w
            where p.ulb.id = :ulbId and p.lastMutationDate is null and w.id in :wardIds
            order by p.propertyNo
            """)
    List<Property> findMissingMutationRecords(Long ulbId, Collection<Long> wardIds);

    @Query("select p from Property p join fetch p.ward where p.ulb.id = :ulbId and p.lastMutationDate is null order by p.propertyNo")
    List<Property> findMissingMutationRecords(Long ulbId);

    long countByUlbId(Long ulbId);
}
