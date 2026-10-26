package in.uk.ulb.ptax.ulb;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WardRepository extends JpaRepository<Ward, Long> {

    List<Ward> findByUlbIdOrderByWardNo(Long ulbId);
}
