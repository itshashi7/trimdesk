package com.groomdesk.franchise;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "franchise")
public class Franchise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "owner_name", nullable = false, length = 120)
    private String ownerName;

    @Column(name = "owner_phone", nullable = false, length = 10)
    private String ownerPhone;

    @Column(length = 15)
    private String gstin;

    @Column(name = "royalty_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal royaltyPercent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Franchise() {
    }

    public Franchise(String name, String ownerName, String ownerPhone, String gstin, BigDecimal royaltyPercent) {
        this.name = name;
        this.ownerName = ownerName;
        this.ownerPhone = ownerPhone;
        this.gstin = gstin;
        this.royaltyPercent = royaltyPercent;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public BigDecimal getRoyaltyPercent() { return royaltyPercent; }
    public void setRoyaltyPercent(BigDecimal royaltyPercent) { this.royaltyPercent = royaltyPercent; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
