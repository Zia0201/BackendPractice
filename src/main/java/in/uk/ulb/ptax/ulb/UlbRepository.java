package in.uk.ulb.ptax.ulb;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UlbRepository extends JpaRepository<Ulb, Long> {

    List<Ulb> findAllByOrderByNameAsc();

    Optional<Ulb> findByCode(String code);
}
