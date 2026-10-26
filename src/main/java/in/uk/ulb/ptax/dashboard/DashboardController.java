package in.uk.ulb.ptax.dashboard;

import in.uk.ulb.ptax.auth.AuthUser;
import in.uk.ulb.ptax.common.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/dashboard")
    public DashboardService.Dashboard dashboard(AuthUser user) {
        user.require(Role.ADMIN, Role.RECORD_KEEPER, Role.CLERK, Role.VERIFIER, Role.FIELD_INSPECTOR,
                Role.APPROVER, Role.COUNTER);
        return dashboardService.forUser(user);
    }
}
