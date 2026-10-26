package in.uk.ulb.ptax.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthService.LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.username(), req.password());
    }

    @GetMapping("/me")
    public AuthUser me(AuthUser user) {
        return user;
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        authService.logout(AuthInterceptor.extractToken(request));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }
}
