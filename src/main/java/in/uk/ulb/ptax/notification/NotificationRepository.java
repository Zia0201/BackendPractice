package in.uk.ulb.ptax.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderBySentAtDesc(Pageable pageable);

    List<Notification> findByUlbIdOrderBySentAtDesc(Long ulbId, Pageable pageable);

    List<Notification> findByRecipientInOrderBySentAtDesc(Collection<String> recipients, Pageable pageable);
}
