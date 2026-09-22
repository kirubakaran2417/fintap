package com.fintap.digikadai.web;

import com.fintap.digikadai.domain.Payment;
import com.fintap.digikadai.repo.PaymentRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

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
        String shop = payment == null || payment.getMerchant() == null ? "FinTap" : HtmlUtils.htmlEscape(payment.getMerchant().getShopName());
        String safeOrder = HtmlUtils.htmlEscape(orderId);
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>FinTap · Visa & Mastercard Tap Success</title>
                  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@600;700;800&family=Plus+Jakarta+Sans:wght@400;500;600&display=swap" rel="stylesheet">
                  <style>
                    body {
                      font-family: 'Plus Jakarta Sans', sans-serif;
                      background: #060b13;
                      color: #fff;
                      margin: 0;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      min-height: 100vh;
                      padding: 20px;
                    }
                    .receipt-card {
                      max-width: 440px;
                      width: 100%%;
                      background: #0c1424;
                      border-radius: 24px;
                      border: 1px solid rgba(255, 255, 255, 0.08);
                      box-shadow: 0 25px 60px rgba(0, 0, 0, 0.7);
                      padding: 36px 28px;
                      text-align: center;
                    }
                    .icon-circle {
                      width: 76px;
                      height: 76px;
                      border-radius: 50%%;
                      background: #00e5b7;
                      color: #000;
                      font-size: 38px;
                      font-weight: 900;
                      margin: 0 auto 20px;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      box-shadow: 0 0 30px rgba(0, 229, 183, 0.4);
                    }
                    h1 { font-family: 'Outfit', sans-serif; font-size: 24px; font-weight: 800; margin-bottom: 6px; }
                    .amount-display { font-family: 'Outfit', sans-serif; font-size: 40px; font-weight: 800; color: #00e5b7; margin: 16px 0; }
                    .detail-row {
                      display: flex;
                      justify-content: space-between;
                      padding: 10px 0;
                      border-bottom: 1px solid rgba(255, 255, 255, 0.06);
                      font-size: 13px;
                      color: #94a3b8;
                    }
                    .detail-row span:last-child { color: #fff; font-weight: 600; }
                    .btn-done {
                      width: 100%%;
                      padding: 14px;
                      background: #00e5b7;
                      color: #000;
                      font-weight: 800;
                      font-size: 15px;
                      border: none;
                      border-radius: 12px;
                      margin-top: 24px;
                      cursor: pointer;
                    }
                  </style>
                </head>
                <body>
                  <div class="receipt-card">
                    <div class="icon-circle">✓</div>
                    <h1>Contactless Card Tap Approved</h1>
                    <div class="amount-display">%s</div>
                    <div class="detail-row">
                      <span>Merchant</span>
                      <span>%s</span>
                    </div>
                    <div class="detail-row">
                      <span>Order Reference</span>
                      <span>%s</span>
                    </div>
                    <div class="detail-row">
                      <span>Payment Method</span>
                      <span>Visa / Mastercard SoftPOS (NFC)</span>
                    </div>
                    <button class="btn-done" onclick="window.close(); if(!window.closed) window.history.back();">Done</button>
                  </div>
                </body>
                </html>
                """.formatted(amount, shop, safeOrder);
    }
}
