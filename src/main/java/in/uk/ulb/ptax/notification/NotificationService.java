package in.uk.ulb.ptax.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * There is no SMS gateway / SMTP wired up for the demo. Messages are written to the
 * notification table (and the log) so they can be shown on screen instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String REGISTRATION_DEPT_EMAIL = "registration.dept@uk.gov.in";

    private final NotificationRepository repository;

    public void sms(String mobile, String message, Long ulbId, String refType, Long refId) {
        if (mobile == null || mobile.isBlank()) {
            return;
        }
        save("SMS", mobile, message, ulbId, refType, refId);
    }

    public void email(String email, String message, Long ulbId, String refType, Long refId) {
        if (email == null || email.isBlank()) {
            return;
        }
        save("EMAIL", email, message, ulbId, refType, refId);
    }

    public void smsAndEmail(String mobile, String email, String message, Long ulbId, String refType, Long refId) {
        sms(mobile, message, ulbId, refType, refId);
        email(email, message, ulbId, refType, refId);
    }

    private void save(String channel, String recipient, String message, Long ulbId, String refType, Long refId) {
        Notification n = new Notification();
        n.setChannel(channel);
        n.setRecipient(recipient);
        n.setMessage(message);
        n.setUlbId(ulbId);
        n.setRefType(refType);
        n.setRefId(refId);
        repository.save(n);
        log.info("[{}] to {}: {}", channel, recipient, message);
    }
}
