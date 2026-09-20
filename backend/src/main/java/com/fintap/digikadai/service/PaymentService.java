package com.fintap.digikadai.service;

import com.fintap.digikadai.domain.CustomerProfile;
import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.domain.Payment;
import com.fintap.digikadai.domain.PaymentRail;
import com.fintap.digikadai.domain.TransactionStatus;
import com.fintap.digikadai.dto.AcceptPaymentRequest;
import com.fintap.digikadai.dto.PaymentDto;
import com.fintap.digikadai.dto.RoutingAdviceDto;
import com.fintap.digikadai.integration.mastercard.MastercardGatewayService;
import com.fintap.digikadai.repo.CustomerProfileRepository;
import com.fintap.digikadai.repo.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final CustomerProfileRepository profiles;
    private final MastercardGatewayService mastercard;

    public PaymentService(
            PaymentRepository payments,
            CustomerProfileRepository profiles,
            MastercardGatewayService mastercard
    ) {
        this.payments = payments;
        this.profiles = profiles;
        this.mastercard = mastercard;
    }

    public RoutingAdviceDto route(BigDecimal amount) {
        boolean cardProfitable = amount.compareTo(new BigDecimal("200")) >= 0;
        if (cardProfitable) {
            BigDecimal mdr = amount.multiply(new BigDecimal("0.015")).setScale(2, RoundingMode.HALF_UP);
            return new RoutingAdviceDto(
                    PaymentRail.CARD,
                    "Ticket size is high enough for card MDR without hurting the kirana margin.",
                    mdr,
                    true
            );
        }
        return new RoutingAdviceDto(
                PaymentRail.UPI,
                "Small ticket — keep this on UPI so the merchant does not pay MDR.",
                BigDecimal.ZERO,
                true
        );
    }

    @Transactional
    public PaymentDto accept(Merchant merchant, AcceptPaymentRequest request) {
        Payment payment = new Payment();
        payment.setMerchant(merchant);
        payment.setAmount(request.amount());
        payment.setRail(request.rail());
        payment.setStatus(TransactionStatus.SUCCESS);
        payment.setCustomerLabel(request.customerLabel() == null || request.customerLabel().isBlank()
                ? (request.rail() == PaymentRail.CARD ? "Card customer" : "Walk-in")
                : request.customerLabel());
        if (request.rail() == PaymentRail.CARD) {
            MastercardGatewayService.CheckoutSession session = mastercard.createCheckout(request.amount());
            payment.setReference(session.orderId());
            payment.setGatewayOrderId(session.orderId());
            payment.setGatewaySessionId(session.sessionId());
            payment.setCheckoutUrl(session.checkoutUrl());
            payment.setStatus(session.live() ? TransactionStatus.PENDING : TransactionStatus.SUCCESS);
            payment.setNote(session.live() ? "MPGS checkout session" : "Local SoftPOS (Mastercard sandbox not configured)");
            String token = "tok_" + Integer.toHexString(payment.getCustomerLabel().hashCode());
            payment.setCardToken(token);
            if (!session.live()) {
                upsertProfile(merchant, token, payment.getCustomerLabel(), request.amount());
            }
        } else {
            payment.setReference("UPI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        return toDto(payments.save(payment));
    }

    public List<PaymentDto> recent(Merchant merchant) {
        return payments.findByMerchantOrderByCreatedAtDesc(merchant).stream().map(this::toDto).toList();
    }

    public List<Payment> todaySuccess(Merchant merchant) {
        Instant start = Instant.now().minusSeconds(18 * 3600);
        return payments.findByMerchantAndCreatedAtAfterAndStatus(merchant, start, TransactionStatus.SUCCESS);
    }

    private void upsertProfile(Merchant merchant, String token, String name, BigDecimal amount) {
        CustomerProfile profile = profiles.findByMerchantAndToken(merchant, token).orElseGet(() -> {
            CustomerProfile created = new CustomerProfile();
            created.setMerchant(merchant);
            created.setToken(token);
            created.setDisplayName(name);
            return created;
        });
        profile.setVisitCount(profile.getVisitCount() + 1);
        profile.setLifetimeSpend(profile.getLifetimeSpend().add(amount));
        profile.setLastVisit(Instant.now());
        profile.setChurnRisk(profile.getVisitCount() < 3 ? 0.42 : 0.12);
        profiles.save(profile);
    }

    private PaymentDto toDto(Payment payment) {
        return new PaymentDto(
                payment.getId(),
                payment.getAmount(),
                payment.getRail(),
                payment.getStatus(),
                payment.getCustomerLabel(),
                payment.getReference(),
                payment.getCreatedAt(),
                payment.getCheckoutUrl(),
                payment.getGatewayOrderId()
        );
    }
}
