package com.groomdesk.service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Central service menu (same for every outlet), readable by any signed-in role. */
@RestController
@RequestMapping("/api/services")
public class MenuController {

    public record ServiceDto(long id, String code, String name, ServiceCategory category,
                             BigDecimal price, int durationMin) {

        static ServiceDto from(GroomingService s) {
            return new ServiceDto(s.getId(), s.getCode(), s.getName(), s.getCategory(), s.getPrice(), s.getDurationMin());
        }
    }

    private final GroomingServiceRepository services;

    public MenuController(GroomingServiceRepository services) {
        this.services = services;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ServiceDto> activeMenu() {
        return services.findByActiveTrueOrderByCodeAscNameAsc().stream().map(ServiceDto::from).toList();
    }
}
