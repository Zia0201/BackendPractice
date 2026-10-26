package in.uk.ulb.ptax.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String channel;
    private String recipient;
    private String message;
    private String refType;
    private Long refId;
    private Long ulbId;
    private LocalDateTime sentAt;

    @PrePersist
    void onCreate() {
        sentAt = LocalDateTime.now();
    }
}
