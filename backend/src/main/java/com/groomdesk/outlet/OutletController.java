package com.groomdesk.outlet;

import com.groomdesk.auth.AccessScope;
import com.groomdesk.auth.CurrentUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/** Outlets the caller may see (HQ: all, owner: their franchise, manager/cashier: their own). */
@RestController
@RequestMapping("/api/outlets")
public class OutletController {

    public record OutletDto(long id, String code, String name, String area, String city, String franchiseName) {
    }

    private final OutletRepository outlets;
    private final AccessScope access;

    public OutletController(OutletRepository outlets, AccessScope access) {
        this.outlets = outlets;
        this.access = access;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<OutletDto> visibleOutlets(CurrentUser user) {
        return outlets.findAll().stream()
                .filter(o -> o.isActive() && access.canAccessOutlet(user, o))
                .sorted(Comparator.comparing(Outlet::getName))
                .map(o -> new OutletDto(o.getId(), o.getCode(), o.getName(), o.getArea(), o.getCity(),
                        o.getFranchise().getName()))
                .toList();
    }
}
