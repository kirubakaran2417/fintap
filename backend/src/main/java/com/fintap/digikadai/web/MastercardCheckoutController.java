package com.fintap.digikadai.web;

import com.fintap.digikadai.config.IntegrationProperties;
import com.fintap.digikadai.domain.Payment;
import com.fintap.digikadai.repo.PaymentRepository;
import com.fintap.digikadai.service.PaymentService;
import org.springframework.http.MediaType;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MastercardCheckoutController {

    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final IntegrationProperties properties;

    public MastercardCheckoutController(PaymentRepository payments, PaymentService paymentService,
                                        IntegrationProperties properties) {
        this.payments = payments;
        this.paymentService = paymentService;
        this.properties = properties;
    }

    @GetMapping(value = "/pay/mastercard/{orderId}", produces = MediaType.TEXT_HTML_VALUE)
    public String checkout(@PathVariable String orderId) {
        Payment payment = requireMastercardPayment(orderId);
        String sessionId = HtmlUtils.htmlEscape(payment.getGatewaySessionId());
        String scriptUrl = HtmlUtils.htmlEscape(properties.getMastercard().getCheckoutScriptUrl());
        String safeOrder = HtmlUtils.htmlEscape(orderId);
        String amount = payment.getAmount() == null ? "0.00" : payment.getAmount().toPlainString();
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width,initial-scale=1"/>
                <title>FinTap Mastercard checkout</title>
                <style>body{font-family:Segoe UI,sans-serif;background:#f5f7fa;margin:0;color:#1a1a2e}
                main{max-width:420px;margin:10vh auto;background:white;padding:28px;border-radius:12px;text-align:center}
                button{background:#1c4587;color:white;border:0;border-radius:8px;padding:14px 22px;font-weight:700}</style>
                <script src="%s" data-error="checkoutError" data-cancel="checkoutCancelled"></script>
                <script>
                  function checkoutError(error){document.getElementById('message').textContent='Checkout error: '+JSON.stringify(error);}
                  function checkoutCancelled(){location.href='/pay/mastercard/%s/cancel';}
                  function start(){Checkout.configure({session:{id:'%s'}});Checkout.showPaymentPage();}
                </script></head><body><main><h1>Card payment</h1><h2>INR %s</h2>
                <p id="message">Order %s</p><button onclick="start()">Continue to secure checkout</button></main></body></html>
                """.formatted(scriptUrl, safeOrder, sessionId, HtmlUtils.htmlEscape(amount), safeOrder);
    }

    @GetMapping(value = "/pay/mastercard/{orderId}/return", produces = MediaType.TEXT_HTML_VALUE)
    public String returned(@PathVariable String orderId) {
        var payment = paymentService.reconcileMastercard(orderId);
        return resultPage(payment.status().name(), orderId);
    }

    @GetMapping(value = "/pay/mastercard/{orderId}/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public String cancelled(@PathVariable String orderId) {
        var payment = paymentService.cancelMastercard(orderId);
        return resultPage(payment.status().name(), orderId);
    }

    private Payment requireMastercardPayment(String orderId) {
        Payment payment = payments.findByGatewayOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        if (!"MASTERCARD".equals(payment.getGatewayProvider()) || payment.getGatewaySessionId() == null) {
            throw new IllegalArgumentException("Mastercard checkout is not available for this payment");
        }
        return payment;
    }

    private String resultPage(String status, String orderId) {
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width,initial-scale=1"/>
                <title>FinTap payment</title></head><body style="font-family:Segoe UI,sans-serif;text-align:center;padding:12vh 20px">
                <h1>Payment %s</h1><p>Order %s</p><p>You can return to FinTap.</p></body></html>
                """.formatted(HtmlUtils.htmlEscape(status), HtmlUtils.htmlEscape(orderId));
    }
}
