package com.groomdesk.staff;

import com.groomdesk.auth.CurrentUser;
import com.groomdesk.staff.BarberService.BarberDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BarberController {

    private final BarberService barberService;

    public BarberController(BarberService barberService) {
        this.barberService = barberService;
    }

    @GetMapping("/api/outlets/{outletId}/barbers")
    public List<BarberDto> barbers(CurrentUser user, @PathVariable long outletId) {
        return barberService.activeBarbers(user, outletId);
    }
}
