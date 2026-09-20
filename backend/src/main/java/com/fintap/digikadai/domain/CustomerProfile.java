package com.fintap.digikadai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Merchant merchant;

    private String token;
    private String displayName;
    private int visitCount;
    private BigDecimal lifetimeSpend = BigDecimal.ZERO;
    private Instant lastVisit = Instant.now();
    private double churnRisk;

    public Long getId() {
        return id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(int visitCount) {
        this.visitCount = visitCount;
    }

    public BigDecimal getLifetimeSpend() {
        return lifetimeSpend;
    }

    public void setLifetimeSpend(BigDecimal lifetimeSpend) {
        this.lifetimeSpend = lifetimeSpend;
    }

    public Instant getLastVisit() {
        return lastVisit;
    }

    public void setLastVisit(Instant lastVisit) {
        this.lastVisit = lastVisit;
    }

    public double getChurnRisk() {
        return churnRisk;
    }

    public void setChurnRisk(double churnRisk) {
        this.churnRisk = churnRisk;
    }
}
