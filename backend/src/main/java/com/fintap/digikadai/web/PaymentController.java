package com.fintap.digikadai.web;

import com.fintap.digikadai.domain.Merchant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fintap.digikadai.dto.AcceptPaymentRequest;
import com.fintap.digikadai.dto.HomeSummaryDto;
import com.fintap.digikadai.dto.PaymentDto;
import com.fintap.digikadai.dto.RoutingAdviceDto;
import com.fintap.digikadai.integration.mastercard.MastercardGatewayService;
import com.fintap.digikadai.service.CommerceService;
import com.fintap.digikadai.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService payments;
    private final CommerceService commerce;
    private final MastercardGatewayService mastercard;

    public PaymentController(PaymentService payments, CommerceService commerce, MastercardGatewayService mastercard) {
        this.payments = payments;
        this.commerce = commerce;
        this.mastercard = mastercard;
    }

    @GetMapping("/home")
    public HomeSummaryDto home(@ModelAttribute Merchant merchant) {
        return commerce.home(merchant);
    }

    @GetMapping("/payments")
    public List<PaymentDto> payments(
            @ModelAttribute Merchant merchant,
            @RequestParam(required = false) String rail
    ) {
        List<PaymentDto> list = payments.recent(merchant);
        if (rail != null && !rail.isBlank() && !"ALL".equalsIgnoreCase(rail)) {
            return list.stream()
                    .filter(p -> p.rail() != null && p.rail().name().equalsIgnoreCase(rail))
                    .toList();
        }
        return list;
    }

    @GetMapping("/payments/routing")
    public RoutingAdviceDto routing(@RequestParam BigDecimal amount) {
        return payments.route(amount);
    }

    @PostMapping("/payments/accept")
    public PaymentDto accept(@ModelAttribute Merchant merchant, @Valid @RequestBody AcceptPaymentRequest request) {
        return payments.accept(merchant, request);
    }

    @GetMapping("/payments/mastercard/orders/{orderId}")
    public Map<String, Object> mastercardOrder(@PathVariable String orderId) {
        return mastercard.retrieveOrder(orderId);
    }

    @GetMapping("/payments/{id}/status")
    public PaymentDto paymentStatus(@ModelAttribute Merchant merchant, @PathVariable Long id,
                                    @RequestParam(defaultValue = "true") boolean refresh) {
        return payments.status(merchant, id, refresh);
    }

    @PostMapping("/payments/{id}/nfc-tap")
    public PaymentDto nfcTap(@ModelAttribute Merchant merchant, @PathVariable Long id,
                             @RequestBody(required = false) Map<String, String> body) {
        String brand = body != null ? body.get("brand") : "VISA";
        String panLast4 = body != null ? body.get("panLast4") : "4242";
        return payments.completeNfcTap(merchant, id, brand, panLast4);
    }

    @PostMapping("/payments/{id}/mastercard/device")
    public PaymentDto mastercardDevice(@ModelAttribute Merchant merchant, @PathVariable Long id,
                                       @RequestBody MastercardDeviceRequest request) {
        return payments.submitMastercardDevicePayment(merchant, id, request.sessionId(), request.devicePayment());
    }

    public record MastercardDeviceRequest(String sessionId, JsonNode devicePayment) { }
}
