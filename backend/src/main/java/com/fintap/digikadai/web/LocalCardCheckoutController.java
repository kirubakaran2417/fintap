package com.fintap.digikadai.web;

import com.fintap.digikadai.domain.Payment;
import com.fintap.digikadai.repo.PaymentRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocalCardCheckoutController {

    private final PaymentRepository payments;

    public LocalCardCheckoutController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping(value = {"/pay/card/{orderId}", "/api/payments/mastercard/local/{orderId}"}, produces = MediaType.TEXT_HTML_VALUE)
    public String receipt(@PathVariable String orderId) {
        Payment payment = payments.findByGatewayOrderId(orderId).orElse(null);
        String amount = payment == null || payment.getAmount() == null ? "—" : "₹" + payment.getAmount().toPlainString();
        String shop = payment == null || payment.getMerchant() == null ? "FinTap" : payment.getMerchant().getShopName();
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>FinTap · Card collected</title>
                  <style>
                    body { margin:0; font-family: Segoe UI, sans-serif; background:#071526; color:#fff; }
                    .wrap { max-width:420px; margin:12vh auto; padding:28px; background:#0B3A67; border-radius:24px; text-align:center; }
                    .ok { width:72px; height:72px; border-radius:50%%; background:#0F9D8A; margin:0 auto 18px; line-height:72px; font-size:36px; }
                    h1 { margin:0 0 8px; font-size:22px; }
                    p { color:#d7e8e4; }
                    .amt { font-size:32px; font-weight:800; margin:16px 0; }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <div class="ok">✓</div>
                    <h1>Card tap successful</h1>
                    <div class="amt">%s</div>
                    <p>%s<br/>Order %s</p>
                    <p>Local SoftPOS. Connect Mastercard MPGS keys to use hosted checkout.</p>
                  </div>
                </body>
                </html>
                """.formatted(amount, shop, orderId);
    }
}
