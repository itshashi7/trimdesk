package com.groomdesk.service;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * An item on the central service menu (table {@code service}).
 * Named GroomingService to avoid clashing with Spring's {@code @Service}.
 */
@Entity
@Table(name = "service")
public class GroomingService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Billing shortcut key, e.g. "1". Optional. */
    @Column(unique = true, length = 4)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ServiceCategory category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;

    @Column(nullable = false)
    private boolean active = true;

    protected GroomingService() {
    }

    public GroomingService(String code, String name, ServiceCategory category, BigDecimal price, Integer durationMin) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.price = price;
        this.durationMin = durationMin;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ServiceCategory getCategory() { return category; }
    public void setCategory(ServiceCategory category) { this.category = category; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getDurationMin() { return durationMin; }
    public void setDurationMin(Integer durationMin) { this.durationMin = durationMin; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
