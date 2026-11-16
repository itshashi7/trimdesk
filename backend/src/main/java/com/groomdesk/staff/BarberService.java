package com.groomdesk.staff;

import com.groomdesk.auth.AccessScope;
import com.groomdesk.auth.CurrentUser;
import com.groomdesk.outlet.Outlet;
import com.groomdesk.outlet.OutletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BarberService {

    public record BarberDto(long id, String name) {
    }

    private final OutletRepository outlets;
    private final BarberRepository barbers;
    private final AccessScope access;

    public BarberService(OutletRepository outlets, BarberRepository barbers, AccessScope access) {
        this.outlets = outlets;
        this.barbers = barbers;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<BarberDto> activeBarbers(CurrentUser user, long outletId) {
        Outlet outlet = outlets.findById(outletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outlet not found"));
        access.requireOutlet(user, outlet);
        return barbers.findByOutletIdAndActiveTrueOrderByName(outletId).stream()
                .map(b -> new BarberDto(b.getId(), b.getName()))
                .toList();
    }
}
