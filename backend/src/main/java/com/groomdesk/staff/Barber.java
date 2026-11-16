package com.groomdesk.staff;

import com.groomdesk.outlet.Outlet;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "barber")
public class Barber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outlet_id", nullable = false)
    private Outlet outlet;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 10)
    private String phone;

    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    @Column(nullable = false)
    private boolean active = true;

    protected Barber() {
    }

    public Barber(Outlet outlet, String name, String phone, BigDecimal commissionPercent) {
        this.outlet = outlet;
        this.name = name;
        this.phone = phone;
        this.commissionPercent = commissionPercent;
    }

    public Long getId() { return id; }
    public Outlet getOutlet() { return outlet; }
    public void setOutlet(Outlet outlet) { this.outlet = outlet; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public BigDecimal getCommissionPercent() { return commissionPercent; }
    public void setCommissionPercent(BigDecimal commissionPercent) { this.commissionPercent = commissionPercent; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
