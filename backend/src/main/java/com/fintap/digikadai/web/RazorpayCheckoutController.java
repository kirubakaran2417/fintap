package com.fintap.digikadai.web;

import com.fintap.digikadai.config.IntegrationProperties;
import com.fintap.digikadai.domain.Payment;
import com.fintap.digikadai.domain.TransactionStatus;
import com.fintap.digikadai.repo.PaymentRepository;
import com.fintap.digikadai.service.PaymentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RazorpayCheckoutController {

    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final IntegrationProperties properties;

    public RazorpayCheckoutController(
            PaymentRepository payments,
            PaymentService paymentService,
            IntegrationProperties properties
    ) {
        this.payments = payments;
        this.paymentService = paymentService;
        this.properties = properties;
    }

    @GetMapping(value = "/pay/razorpay/{orderId}", produces = MediaType.TEXT_HTML_VALUE)
    public String checkout(@PathVariable String orderId) {
        Payment payment = payments.findByGatewayOrderId(orderId).orElse(null);
        String amount = payment == null || payment.getAmount() == null ? "0" : payment.getAmount().toPlainString();
        String shop = payment == null || payment.getMerchant() == null ? "FinTap" : payment.getMerchant().getShopName();
        String key = properties.getRazorpay().getKeyId();
        long paise = payment == null || payment.getAmount() == null
                ? 100
                : payment.getAmount().movePointRight(2).longValue();
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>FinTap · Razorpay</title>
                  <script src="https://checkout.razorpay.com/v1/checkout.js"></script>
                  <style>
                    body { margin:0; font-family: Segoe UI, sans-serif; background:#071526; color:#fff; }
                    .wrap { max-width:420px; margin:12vh auto; padding:28px; background:#0B3A67; border-radius:24px; text-align:center; }
                    button { background:#0F9D8A; color:#fff; border:0; padding:14px 22px; border-radius:12px; font-weight:700; cursor:pointer; }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <h1>Pay with Razorpay</h1>
                    <p>%s</p>
                    <p style="font-size:28px;font-weight:800">₹%s</p>
                    <p>Test mode. Use Razorpay test cards.</p>
                    <button id="pay">Open Razorpay checkout</button>
                  </div>
                  <script>
                    const options = {
                      key: "%s",
                      amount: %s,
                      currency: "INR",
                      name: "%s",
                      description: "FinTap card collect",
                      order_id: "%s",
                      handler: function () {
                        window.location = "/pay/razorpay/%s/success";
                      }
                    };
                    document.getElementById('pay').onclick = function () {
                      new Razorpay(options).open();
                    };
                    new Razorpay(options).open();
                  </script>
                </body>
                </html>
                """.formatted(shop, amount, key, paise, shop.replace("\"", ""), orderId, orderId);
    }

    @GetMapping(value = "/pay/razorpay/{orderId}/success", produces = MediaType.TEXT_HTML_VALUE)
    public String success(@PathVariable String orderId) {
        try {
            paymentService.markSuccess(orderId);
        } catch (Exception ignored) {
        }
        Payment payment = payments.findByGatewayOrderId(orderId).orElse(null);
        String status = payment == null ? "UNKNOWN" : payment.getStatus() == TransactionStatus.SUCCESS ? "SUCCESS" : String.valueOf(payment.getStatus());
        return """
                <!doctype html>
                <html><body style="font-family:Segoe UI;background:#071526;color:#fff;text-align:center;padding:80px">
                <h1>Payment %s</h1>
                <p>Order %s</p>
                <p>You can close this tab and return to FinTap.</p>
                </body></html>
                """.formatted(status, orderId);
    }
}
