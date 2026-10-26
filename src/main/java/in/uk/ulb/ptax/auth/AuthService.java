package in.uk.ulb.ptax.auth;

import in.uk.ulb.ptax.common.ApiException;
import in.uk.ulb.ptax.ulb.Ward;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        AppUser user = userRepository.findByUsername(username.trim().toLowerCase())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        String token = tokenStore.issue(user.getId());
        return new LoginResponse(token, toAuthUser(user));
    }

    @Transactional(readOnly = true)
    public AuthUser loadByToken(String token) {
        Long userId = tokenStore.resolve(token);
        if (userId == null) {
            return null;
        }
        return userRepository.findWithWardsById(userId)
                .filter(AppUser::isActive)
                .map(this::toAuthUser)
                .orElse(null);
    }

    public void logout(String token) {
        tokenStore.revoke(token);
    }

    private AuthUser toAuthUser(AppUser u) {
        var wardIds = u.getWards().stream().map(Ward::getId).collect(Collectors.toSet());
        return new AuthUser(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.getUlbId(),
                u.getMobile(), u.getEmail(), wardIds);
    }

    public record LoginResponse(String token, AuthUser user) {
    }
}
