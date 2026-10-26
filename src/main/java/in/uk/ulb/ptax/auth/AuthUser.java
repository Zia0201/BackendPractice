package in.uk.ulb.ptax.auth;

import in.uk.ulb.ptax.common.ApiException;
import in.uk.ulb.ptax.common.Role;

import java.util.Arrays;
import java.util.Set;

/**
 * The logged in user for the current request. Resolved from the bearer token
 * and injected into controller methods.
 */
public record AuthUser(Long id, String username, String fullName, Role role, Long ulbId,
                       String mobile, String email, Set<Long> wardIds) {

    public boolean is(Role... roles) {
        return Arrays.asList(roles).contains(role);
    }

    public void require(Role... roles) {
        if (!is(roles)) {
            throw ApiException.forbidden("You are not allowed to do this as " + role);
        }
    }

    /** Admin can see everything, staff only their own ULB. */
    public void checkUlb(Long ulbId) {
        if (role == Role.ADMIN) {
            return;
        }
        if (role.isUlbStaff() && this.ulbId.equals(ulbId)) {
            return;
        }
        throw ApiException.forbidden("This record belongs to another ULB");
    }

    public boolean restrictedToWards() {
        return wardIds != null && !wardIds.isEmpty();
    }
}
