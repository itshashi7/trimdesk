package com.groomdesk.billing;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** One part of a split bill, e.g. ₹100 cash of a ₹500 bill. Only MIXED bills have these rows. */
@Entity
@Table(name = "bill_payment")
public class BillPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentMode mode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected BillPayment() {
    }

    public BillPayment(PaymentMode mode, BigDecimal amount) {
        this.mode = mode;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public Bill getBill() { return bill; }
    void setBill(Bill bill) { this.bill = bill; }
    public PaymentMode getMode() { return mode; }
    public BigDecimal getAmount() { return amount; }
}
