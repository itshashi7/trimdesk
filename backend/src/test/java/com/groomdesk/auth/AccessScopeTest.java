package com.groomdesk.auth;

import com.groomdesk.franchise.Franchise;
import com.groomdesk.outlet.Outlet;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessScopeTest {

    private final AccessScope access = new AccessScope();

    private final Franchise franchise1 = franchise(1L);
    private final Franchise franchise2 = franchise(2L);
    private final Outlet tnr = outlet(1L, "TNR", franchise1);
    private final Outlet ady = outlet(5L, "ADY", franchise2);

    private final CurrentUser hq = new CurrentUser(1, "hq", Role.HQ_ADMIN, null, null);
    private final CurrentUser owner2 = new CurrentUser(3, "owner2", Role.FRANCHISE_OWNER, 2L, null);
    private final CurrentUser managerAdy = new CurrentUser(14, "manager_ady", Role.OUTLET_MANAGER, null, 5L);
    private final CurrentUser cashierAdy = new CurrentUser(15, "cashier_ady", Role.CASHIER, null, 5L);

    @Test
    void hqSeesEveryOutlet() {
        assertThat(access.canAccessOutlet(hq, tnr)).isTrue();
        assertThat(access.canAccessOutlet(hq, ady)).isTrue();
    }

    @Test
    void franchiseOwnerSeesOnlyTheirFranchiseOutlets() {
        assertThat(access.canAccessOutlet(owner2, ady)).isTrue();
        assertThat(access.canAccessOutlet(owner2, tnr)).isFalse();
    }

    @Test
    void managerAndCashierSeeOnlyTheirOutlet() {
        assertThat(access.canAccessOutlet(managerAdy, ady)).isTrue();
        assertThat(access.canAccessOutlet(managerAdy, tnr)).isFalse();
        assertThat(access.canAccessOutlet(cashierAdy, ady)).isTrue();
        assertThat(access.canAccessOutlet(cashierAdy, tnr)).isFalse();
    }

    @Test
    void requireOutletThrowsAccessDenied() {
        assertThatThrownBy(() -> access.requireOutlet(cashierAdy, tnr)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyCashiersAndManagersBillAndAlwaysAtTheirOwnOutlet() {
        assertThat(access.requireHomeOutlet(cashierAdy)).isEqualTo(5L);
        assertThat(access.requireHomeOutlet(managerAdy)).isEqualTo(5L);
        assertThatThrownBy(() -> access.requireHomeOutlet(hq)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> access.requireHomeOutlet(owner2)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cashierWithoutOutletCannotBill() {
        CurrentUser orphan = new CurrentUser(99, "cashier_x", Role.CASHIER, null, null);
        assertThatThrownBy(() -> access.requireHomeOutlet(orphan)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireAnyRoleRejectsOtherRoles() {
        assertThatThrownBy(() -> access.requireAnyRole(owner2, Role.CASHIER, Role.HQ_ADMIN))
                .isInstanceOf(AccessDeniedException.class);
        access.requireAnyRole(hq, Role.CASHIER, Role.HQ_ADMIN);
    }

    private static Franchise franchise(long id) {
        Franchise f = new Franchise("F" + id, "Owner", "9000000000", null, new BigDecimal("8.00"));
        ReflectionTestUtils.setField(f, "id", id);
        return f;
    }

    private static Outlet outlet(long id, String code, Franchise f) {
        Outlet o = new Outlet(f, code, code, null, "Chennai", new BigDecimal("18.00"));
        ReflectionTestUtils.setField(o, "id", id);
        return o;
    }
}
