package in.uk.ulb.ptax.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed users are inserted by Flyway without a password. On startup we give
 * them the demo password so we don't have to keep bcrypt hashes in SQL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoPasswordInitializer implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.demo.default-password}")
    private String defaultPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var users = userRepository.findByPasswordHashIsNull();
        if (users.isEmpty()) {
            return;
        }
        String hash = passwordEncoder.encode(defaultPassword);
        users.forEach(u -> u.setPasswordHash(hash));
        log.info("Set demo password for {} users", users.size());
    }
}
