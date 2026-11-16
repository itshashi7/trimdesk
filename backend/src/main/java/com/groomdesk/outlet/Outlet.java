package com.groomdesk.outlet;

import com.groomdesk.franchise.Franchise;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "outlet")
public class Outlet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "franchise_id", nullable = false)
    private Franchise franchise;

    /** Short outlet code used in bill numbers, e.g. "MNP". */
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String area;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(nullable = false)
    private boolean active = true;

    protected Outlet() {
    }

    public Outlet(Franchise franchise, String code, String name, String area, String city, BigDecimal taxRate) {
        this.franchise = franchise;
        this.code = code;
        this.name = name;
        this.area = area;
        this.city = city;
        this.taxRate = taxRate;
    }

    public Long getId() { return id; }
    public Franchise getFranchise() { return franchise; }
    public void setFranchise(Franchise franchise) { this.franchise = franchise; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
