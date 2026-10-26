package in.uk.ulb.ptax.notification;

import in.uk.ulb.ptax.auth.AuthUser;
import in.uk.ulb.ptax.common.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository repository;

    @GetMapping
    public List<Notification> list(AuthUser user, @RequestParam(defaultValue = "50") int limit) {
        var page = PageRequest.of(0, Math.min(limit, 200));

        if (user.role() == Role.ADMIN) {
            return repository.findAllByOrderBySentAtDesc(page);
        }
        if (user.role() == Role.CITIZEN) {
            List<String> mine = new ArrayList<>();
            if (user.mobile() != null) mine.add(user.mobile());
            if (user.email() != null) mine.add(user.email());
            return repository.findByRecipientInOrderBySentAtDesc(mine, page);
        }
        return repository.findByUlbIdOrderBySentAtDesc(user.ulbId(), page);
    }
}
