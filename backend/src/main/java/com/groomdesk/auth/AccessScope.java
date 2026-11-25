package com.groomdesk.auth;

import com.groomdesk.outlet.Outlet;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Role scoping from docs/SPEC.md §4, enforced by services (not only the UI).
 * <ul>
 *   <li>HQ_ADMIN: every outlet</li>
 *   <li>FRANCHISE_OWNER: outlets of their franchise</li>
 *   <li>OUTLET_MANAGER, CASHIER: their own outlet</li>
 * </ul>
 */
@Component
public class AccessScope {

    /** Roles that run the billing counter. */
    public static final Role[] BILLING_ROLES = {Role.CASHIER, Role.OUTLET_MANAGER};

    public void requireAnyRole(CurrentUser user, Role... allowed) {
        if (Arrays.stream(allowed).noneMatch(r -> r == user.role())) {
            throw new AccessDeniedException("Role " + user.role() + " is not allowed to do this");
        }
    }

    public boolean canAccessOutlet(CurrentUser user, Outlet outlet) {
        return switch (user.role()) {
            case HQ_ADMIN -> true;
            case FRANCHISE_OWNER -> user.franchiseId() != null
                    && user.franchiseId().equals(outlet.getFranchise().getId());
            case OUTLET_MANAGER, CASHIER -> user.outletId() != null && user.outletId().equals(outlet.getId());
        };
    }

    public void requireOutlet(CurrentUser user, Outlet outlet) {
        if (!canAccessOutlet(user, outlet)) {
            throw new AccessDeniedException("No access to outlet " + outlet.getCode());
        }
    }

    /** The outlet a billing user works at; bills are always created there, never at a client-chosen outlet. */
    public long requireHomeOutlet(CurrentUser user) {
        requireAnyRole(user, BILLING_ROLES);
        if (user.outletId() == null) {
            throw new AccessDeniedException("User " + user.username() + " is not assigned to an outlet");
        }
        return user.outletId();
    }
}
