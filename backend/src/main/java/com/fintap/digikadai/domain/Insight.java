package com.fintap.digikadai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "insights")
public class Insight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Merchant merchant;

    private String title;
    @Column(length = 2000)
    private String bodyEn;
    @Column(length = 2000)
    private String bodyHi;
    @Column(length = 2000)
    private String bodyTa;
    @Column(length = 2000)
    private String bodyTe;
    private String type;
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBodyEn() {
        return bodyEn;
    }

    public void setBodyEn(String bodyEn) {
        this.bodyEn = bodyEn;
    }

    public String getBodyHi() {
        return bodyHi;
    }

    public void setBodyHi(String bodyHi) {
        this.bodyHi = bodyHi;
    }

    public String getBodyTa() {
        return bodyTa;
    }

    public void setBodyTa(String bodyTa) {
        this.bodyTa = bodyTa;
    }

    public String getBodyTe() {
        return bodyTe;
    }

    public void setBodyTe(String bodyTe) {
        this.bodyTe = bodyTe;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
