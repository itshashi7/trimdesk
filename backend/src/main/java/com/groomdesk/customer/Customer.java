package com.groomdesk.customer;

import com.groomdesk.outlet.Outlet;
import com.groomdesk.staff.Barber;
import jakarta.persistence.*;

import java.time.Instant;

/** Shared across all outlets; identified by phone. */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String phone;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_barber_id")
    private Barber preferredBarber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "first_visit_outlet_id", nullable = false)
    private Outlet firstVisitOutlet;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Customer() {
    }

    public Customer(String phone, String name, Outlet firstVisitOutlet) {
        this.phone = phone;
        this.name = name;
        this.firstVisitOutlet = firstVisitOutlet;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Barber getPreferredBarber() { return preferredBarber; }
    public void setPreferredBarber(Barber preferredBarber) { this.preferredBarber = preferredBarber; }
    public Outlet getFirstVisitOutlet() { return firstVisitOutlet; }
    public void setFirstVisitOutlet(Outlet firstVisitOutlet) { this.firstVisitOutlet = firstVisitOutlet; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
