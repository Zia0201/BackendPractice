package in.uk.ulb.ptax.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token -> user id map. Good enough for the demo; tokens are lost on restart.
 * For production this would be replaced with JWT / state SSO.
 */
@Component
public class TokenStore {

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, userId);
        return token;
    }

    public Long resolve(String token) {
        return token == null ? null : tokens.get(token);
    }

    public void revoke(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }
}
