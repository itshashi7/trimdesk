package com.groomdesk.billing;

import com.groomdesk.service.GroomingService;
import com.groomdesk.staff.Barber;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "bill_item")
public class BillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private GroomingService service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_id", nullable = false)
    private Barber barber;

    /** Snapshot of the menu price at billing time. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    protected BillItem() {
    }

    public BillItem(GroomingService service, Barber barber, BigDecimal price, BigDecimal commissionAmount) {
        this.service = service;
        this.barber = barber;
        this.price = price;
        this.commissionAmount = commissionAmount;
    }

    public Long getId() { return id; }
    public Bill getBill() { return bill; }
    void setBill(Bill bill) { this.bill = bill; }
    public GroomingService getService() { return service; }
    public void setService(GroomingService service) { this.service = service; }
    public Barber getBarber() { return barber; }
    public void setBarber(Barber barber) { this.barber = barber; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }
}
