package com.fintap.digikadai.service;

import com.fintap.digikadai.domain.CustomerProfile;
import com.fintap.digikadai.domain.KhataEntry;
import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.domain.Payment;
import com.fintap.digikadai.domain.PaymentRail;
import com.fintap.digikadai.domain.TransactionStatus;
import com.fintap.digikadai.dto.AcceptPaymentRequest;
import com.fintap.digikadai.dto.PaymentDto;
import com.fintap.digikadai.dto.RoutingAdviceDto;
import com.fintap.digikadai.integration.mastercard.MastercardGatewayService;
import com.fintap.digikadai.repo.CustomerProfileRepository;
import com.fintap.digikadai.repo.KhataEntryRepository;
import com.fintap.digikadai.repo.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final CustomerProfileRepository profiles;
    private final MastercardGatewayService mastercard;
    private final com.fintap.digikadai.integration.razorpay.RazorpayGatewayService razorpay;
    private final KhataEntryRepository khataEntries;

    public PaymentService(
            PaymentRepository payments,
            CustomerProfileRepository profiles,
            MastercardGatewayService mastercard,
            com.fintap.digikadai.integration.razorpay.RazorpayGatewayService razorpay
    ) {
        this(payments, profiles, mastercard, razorpay, null);
    }

    @Autowired
    public PaymentService(
            PaymentRepository payments,
            CustomerProfileRepository profiles,
            MastercardGatewayService mastercard,
            com.fintap.digikadai.integration.razorpay.RazorpayGatewayService razorpay,
            KhataEntryRepository khataEntries
    ) {
        this.payments = payments;
        this.profiles = profiles;
        this.mastercard = mastercard;
        this.razorpay = razorpay;
        this.khataEntries = khataEntries;
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
                : request.customerLabel().trim());
        String mobile = MerchantService.digits(request.customerMobile());
        payment.setCustomerMobile(mobile.isBlank() ? null : mobile);
        if (request.rail() == PaymentRail.CARD) {
            String provider = request.provider() == null ? "AUTO" : request.provider().trim().toUpperCase(Locale.ROOT);
            boolean useMastercard = "MASTERCARD".equals(provider) || ("AUTO".equals(provider) && mastercardReady());
            boolean useRazorpay = "RAZORPAY".equals(provider) || ("AUTO".equals(provider) && !useMastercard && razorpay.ready());
            if ("RAZORPAY".equals(provider) && !razorpay.ready()) {
                fail(payment, "RAZORPAY", "Razorpay is not configured");
                return toDto(payments.save(payment));
            }
            try {
                if (useMastercard) {
                    startMastercard(payment, request.amount());
                } else if (useRazorpay) {
                    startRazorpay(payment, request.amount());
                } else {
                    startLocalCard(payment, merchant, request.amount());
                }
            } catch (RuntimeException primaryFailure) {
                if ("AUTO".equals(provider) && useMastercard && razorpay.ready()) {
                    try {
                        startRazorpay(payment, request.amount());
                        payment.setNote("Razorpay fallback after Mastercard session failure");
                    } catch (RuntimeException fallbackFailure) {
                        fail(payment, "AUTO", "Mastercard failed: " + safeMessage(primaryFailure)
                                + "; Razorpay failed: " + safeMessage(fallbackFailure));
                    }
                } else {
                    fail(payment, useMastercard ? "MASTERCARD" : "RAZORPAY", safeMessage(primaryFailure));
                }
            }
            if (payment.getCardToken() == null) {
                payment.setCardToken(tokenFor(payment));
            }
        } else {
            payment.setReference("UPI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setGatewayProvider("UPI");
        }
        return toDto(payments.save(payment));
    }

    @Transactional
    public PaymentDto markSuccess(String gatewayOrderId) {
        Payment payment = payments.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> new IllegalStateException("Payment not found"));
        payment.setStatus(TransactionStatus.SUCCESS);
        payment.setNote("Razorpay checkout completed");
        if (payment.getCardToken() != null && payment.getMerchant() != null) {
            upsertProfile(payment.getMerchant(), payment.getCardToken(), payment.getCustomerLabel(), payment.getAmount());
        }
        return toDto(payments.save(payment));
    }

    @Transactional
    public PaymentDto reconcileMastercard(String gatewayOrderId) {
        return reconcileMastercard(gatewayOrderId, null);
    }

    @Transactional
    public PaymentDto reconcileMastercard(String gatewayOrderId, String brand) {
        Payment payment = payments.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> new IllegalStateException("Payment not found"));
        if (!"MASTERCARD".equals(payment.getGatewayProvider())) {
            throw new IllegalStateException("Payment is not a Mastercard gateway payment");
        }
        com.fasterxml.jackson.databind.JsonNode order;
        try {
            order = mastercard.retrieveOrderNode(gatewayOrderId);
        } catch (RuntimeException ex) {
            payment.setFailureReason("Mastercard status refresh failed: " + safeMessage(ex));
            payment.setUpdatedAt(Instant.now());
            return toDto(payments.save(payment));
        }
        String gatewayStatus = order.path("order").path("status").asText("").toUpperCase(Locale.ROOT);
        String result = order.path("result").asText("").toUpperCase(Locale.ROOT);
        TransactionStatus previous = payment.getStatus();
        if (Set.of("CAPTURED", "PAID", "PURCHASED", "AUTHORIZED", "APPROVED").contains(gatewayStatus)
                || "SUCCESS".equals(result) && !Set.of("FAILED", "DECLINED", "CANCELLED").contains(gatewayStatus)) {
            payment.setStatus(TransactionStatus.SUCCESS);
            payment.setFailureReason(null);
            if (brand != null && !brand.isBlank()) {
                payment.setNote(brand.toUpperCase(Locale.ROOT) + " Contactless NFC Tap Approved");
            }
            if (payment.getCardToken() == null) {
                payment.setCardToken(tokenFor(payment));
            }
            if (previous != TransactionStatus.SUCCESS && payment.getCardToken() != null) {
                upsertProfile(payment.getMerchant(), payment.getCardToken(), payment.getCustomerLabel(), payment.getAmount());
            }
        } else if (Set.of("FAILED", "DECLINED", "REJECTED").contains(gatewayStatus) || "FAILURE".equals(result)) {
            payment.setStatus(TransactionStatus.FAILED);
            payment.setFailureReason("Mastercard status: " + (gatewayStatus.isBlank() ? result : gatewayStatus));
        } else if ("CANCELLED".equals(gatewayStatus)) {
            payment.setStatus(TransactionStatus.CANCELLED);
            payment.setFailureReason("Checkout cancelled");
        }
        payment.setUpdatedAt(Instant.now());
        return toDto(payments.save(payment));
    }

    @Transactional
    public PaymentDto completeNfcTap(Merchant merchant, Long id, String brand, String panLast4) {
        Payment payment = payments.findById(id)
                .filter(found -> found.getMerchant().getId().equals(merchant.getId()))
                .orElseThrow(() -> new IllegalStateException("Payment not found"));
        payment.setStatus(TransactionStatus.SUCCESS);
        payment.setFailureReason(null);
        String cardBrand = (brand == null || brand.isBlank()) ? "VISA" : brand.toUpperCase(Locale.ROOT);
        String last4 = (panLast4 == null || panLast4.isBlank()) ? "4242" : panLast4;
        payment.setNote(cardBrand + " Contactless NFC Tap (•••• " + last4 + ")");
        if (payment.getCardToken() == null) {
            payment.setCardToken(tokenFor(payment));
        }
        upsertProfile(payment.getMerchant(), payment.getCardToken(), payment.getCustomerLabel(), payment.getAmount());
        payment.setUpdatedAt(Instant.now());
        return toDto(payments.save(payment));
    }

    @Transactional
    public PaymentDto cancelMastercard(String gatewayOrderId) {
        Payment payment = payments.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> new IllegalStateException("Payment not found"));
        if (payment.getStatus() == TransactionStatus.PENDING) {
            payment.setStatus(TransactionStatus.CANCELLED);
            payment.setFailureReason("Checkout cancelled by customer");
            payment.setUpdatedAt(Instant.now());
            payments.save(payment);
        }
        return toDto(payment);
    }

    public PaymentDto status(Merchant merchant, Long id, boolean refresh) {
        Payment payment = payments.findById(id)
                .filter(found -> found.getMerchant().getId().equals(merchant.getId()))
                .orElseThrow(() -> new IllegalStateException("Payment not found"));
        if (refresh && payment.getStatus() == TransactionStatus.PENDING
                && "MASTERCARD".equals(payment.getGatewayProvider())) {
            return reconcileMastercard(payment.getGatewayOrderId());
        }
        return toDto(payment);
    }

    @Transactional
    public PaymentDto submitMastercardDevicePayment(Merchant merchant, Long id,
                                                     String sessionId, com.fasterxml.jackson.databind.JsonNode devicePayment) {
        Payment payment = payments.findById(id)
                .filter(found -> found.getMerchant().getId().equals(merchant.getId()))
                .orElseThrow(() -> new IllegalStateException("Payment not found"));
        if (!"MASTERCARD".equals(payment.getGatewayProvider()) || payment.getStatus() != TransactionStatus.PENDING) {
            throw new IllegalStateException("Payment is not a pending Mastercard transaction");
        }
        Map<String, Object> result = mastercard.payWithDevicePayload(payment.getGatewayOrderId(),
                sessionId == null || sessionId.isBlank() ? payment.getGatewaySessionId() : sessionId, devicePayment);
        if (result.get("ok") != Boolean.TRUE) {
            payment.setStatus(TransactionStatus.FAILED);
            payment.setFailureReason(String.valueOf(result.getOrDefault("error", "Mastercard device payment failed")));
            payment.setUpdatedAt(Instant.now());
            return toDto(payments.save(payment));
        }
        return reconcileMastercard(payment.getGatewayOrderId());
    }

    private boolean mastercardReady() {
        return mastercard.ready();
    }

    private void startMastercard(Payment payment, BigDecimal amount) {
        MastercardGatewayService.CheckoutSession session = mastercard.createCheckout(amount);
        payment.setReference(session.orderId());
        payment.setGatewayOrderId(session.orderId());
        payment.setGatewaySessionId(session.sessionId());
        payment.setCheckoutUrl(session.checkoutUrl());
        payment.setStatus(TransactionStatus.PENDING);
        payment.setNote(session.live() ? "MPGS checkout session" : "Mastercard Hosted Simulator");
        payment.setGatewayProvider("MASTERCARD");
    }

    private void startRazorpay(Payment payment, BigDecimal amount) {
        var order = razorpay.createOrder(amount);
        payment.setReference(order.orderId());
        payment.setGatewayOrderId(order.orderId());
        payment.setGatewaySessionId(order.orderId());
        payment.setCheckoutUrl(order.checkoutUrl());
        payment.setStatus(TransactionStatus.PENDING);
        payment.setNote("Razorpay test order");
        payment.setGatewayProvider("RAZORPAY");
        payment.setFailureReason(null);
    }

    private void startLocalCard(Payment payment, Merchant merchant, BigDecimal amount) {
        MastercardGatewayService.CheckoutSession session = mastercard.createCheckout(amount);
        payment.setReference(session.orderId());
        payment.setGatewayOrderId(session.orderId());
        payment.setGatewaySessionId(session.sessionId());
        payment.setCheckoutUrl(session.checkoutUrl());
        payment.setStatus(TransactionStatus.SUCCESS);
        payment.setNote("Local SoftPOS simulation; no external card gateway configured");
        payment.setGatewayProvider("LOCAL");
        String token = tokenFor(payment);
        payment.setCardToken(token);
        upsertProfile(merchant, token, payment.getCustomerLabel(), amount);
    }

    private void fail(Payment payment, String provider, String reason) {
        payment.setReference("FAILED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment.setGatewayProvider(provider);
        payment.setStatus(TransactionStatus.FAILED);
        payment.setFailureReason(reason == null || reason.isBlank() ? "Gateway request failed" : reason);
        payment.setNote("External card gateway failure");
        payment.setUpdatedAt(Instant.now());
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return "Gateway request failed";
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    public List<PaymentDto> recent(Merchant merchant) {
        List<PaymentDto> list = new ArrayList<>(
                payments.findByMerchantOrderByCreatedAtDesc(merchant).stream().map(this::toDto).toList()
        );
        if (khataEntries != null) {
            List<PaymentDto> khataList = khataEntries.findByMerchantOrderByCreatedAtDesc(merchant).stream()
                    .map(this::toKhataPaymentDto)
                    .toList();
            list.addAll(khataList);
            list.sort(Comparator.comparing(PaymentDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        }
        return list;
    }

    private PaymentDto toKhataPaymentDto(KhataEntry entry) {
        boolean credit = entry.isCredit();
        String note = credit ? "Repayment received" : "Khata credit (Udhaar)";
        if (entry.getNote() != null && !entry.getNote().isBlank()) {
            note += " · " + entry.getNote();
        }
        return new PaymentDto(
                entry.getId(),
                entry.getAmount(),
                PaymentRail.KHATA,
                TransactionStatus.SUCCESS,
                entry.getCustomerName() != null && !entry.getCustomerName().isBlank() ? entry.getCustomerName() : "Customer",
                entry.getMobile(),
                "KHATA-" + (credit ? "REPAY-" : "GIVEN-") + entry.getId(),
                entry.getCreatedAt() != null ? entry.getCreatedAt() : Instant.now(),
                null,
                null,
                null,
                "KHATA",
                note
        );
    }

    public List<Payment> all(Merchant merchant) {
        return payments.findByMerchantOrderByCreatedAtDesc(merchant);
    }

    public List<Payment> todaySuccess(Merchant merchant) {
        Instant start = Instant.now().minusSeconds(18 * 3600);
        return payments.findByMerchantAndCreatedAtAfterAndStatus(merchant, start, TransactionStatus.SUCCESS);
    }

    private String tokenFor(Payment payment) {
        String identity = payment.getCustomerMobile() != null && !payment.getCustomerMobile().isBlank()
                ? payment.getCustomerMobile()
                : payment.getCustomerLabel();
        return "tok_" + Integer.toHexString(identity.hashCode());
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
                payment.getCustomerMobile(),
                payment.getReference(),
                payment.getCreatedAt(),
                payment.getCheckoutUrl(),
                payment.getGatewayOrderId(),
                payment.getGatewaySessionId(),
                payment.getGatewayProvider(),
                payment.getFailureReason()
        );
    }
}
