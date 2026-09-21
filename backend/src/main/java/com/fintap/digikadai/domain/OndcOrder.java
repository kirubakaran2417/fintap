package com.fintap.digikadai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ondc_orders")
public class OndcOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Merchant merchant;

    private String orderRef;
    private String transactionId;
    private String messageId;
    private String providerId;
    private String buyerApp;
    private String itemsSummary;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private OndcOrderStatus status;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private String lastCallbackAction;
    private String callbackStatus;
    private String callbackError;
    @Lob
    private String rawRequest;

    public Long getId() {
        return id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public void setOrderRef(String orderRef) {
        this.orderRef = orderRef;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getBuyerApp() {
        return buyerApp;
    }

    public void setBuyerApp(String buyerApp) {
        this.buyerApp = buyerApp;
    }

    public String getItemsSummary() {
        return itemsSummary;
    }

    public void setItemsSummary(String itemsSummary) {
        this.itemsSummary = itemsSummary;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public OndcOrderStatus getStatus() {
        return status;
    }

    public void setStatus(OndcOrderStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getLastCallbackAction() { return lastCallbackAction; }
    public void setLastCallbackAction(String lastCallbackAction) { this.lastCallbackAction = lastCallbackAction; }
    public String getCallbackStatus() { return callbackStatus; }
    public void setCallbackStatus(String callbackStatus) { this.callbackStatus = callbackStatus; }
    public String getCallbackError() { return callbackError; }
    public void setCallbackError(String callbackError) { this.callbackError = callbackError; }
    public String getRawRequest() { return rawRequest; }
    public void setRawRequest(String rawRequest) { this.rawRequest = rawRequest; }
}
