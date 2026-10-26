package in.uk.ulb.ptax.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = "wards")
    Optional<AppUser> findByUsername(String username);

    @EntityGraph(attributePaths = "wards")
    Optional<AppUser> findWithWardsById(Long id);

    List<AppUser> findByPasswordHashIsNull();
}
