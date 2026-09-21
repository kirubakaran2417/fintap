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
        String sessionId = HtmlUtils.htmlEscape(payment.getGatewaySessionId() == null ? "" : payment.getGatewaySessionId());
        String scriptUrl = HtmlUtils.htmlEscape(properties.getMastercard().getCheckoutScriptUrl());
        String safeOrder = HtmlUtils.htmlEscape(orderId);
        String amount = payment.getAmount() == null ? "0.00" : payment.getAmount().toPlainString();
        String shopName = payment.getMerchant() == null ? "FinTap Merchant" : HtmlUtils.htmlEscape(payment.getMerchant().getShopName());

        boolean isLive = properties.getMastercard().gatewayReady() && !sessionId.startsWith("SESSION-MOCK-MC-");

        if (isLive) {
            return """
                    <!doctype html><html lang="en"><head><meta charset="utf-8"/>
                    <meta name="viewport" content="width=device-width,initial-scale=1"/>
                    <title>FinTap Mastercard Checkout</title>
                    <style>body{font-family:'Segoe UI',Roboto,sans-serif;background:#0A192F;margin:0;color:#fff;display:flex;align-items:center;justify-content:center;min-height:100vh}
                    main{max-width:420px;width:90%%;background:#112240;padding:32px;border-radius:16px;text-align:center;box-shadow:0 10px 30px rgba(0,0,0,0.5)}
                    button{background:#EB001B;color:white;border:0;border-radius:8px;padding:14px 24px;font-weight:700;font-size:16px;cursor:pointer;width:100%%;margin-top:20px}
                    button:hover{background:#cc0018}</style>
                    <script src="%s" data-error="checkoutError" data-cancel="checkoutCancelled"></script>
                    <script>
                      function checkoutError(error){document.getElementById('message').textContent='Checkout error: '+JSON.stringify(error);}
                      function checkoutCancelled(){location.href='/pay/mastercard/%s/cancel';}
                      function start(){Checkout.configure({session:{id:'%s'}});Checkout.showPaymentPage();}
                    </script></head><body><main>
                    <div style="display:flex;justify-content:center;align-items:center;margin-bottom:20px;">
                      <div style="width:36px;height:36px;background:#EB001B;border-radius:50%%;display:inline-block;"></div>
                      <div style="width:36px;height:36px;background:#F79E1B;border-radius:50%%;display:inline-block;margin-left:-14px;opacity:0.9;"></div>
                    </div>
                    <h1>Mastercard Payment</h1>
                    <h2>₹ %s</h2>
                    <p id="message">Order %s</p>
                    <button onclick="start()">Continue to MPGS Secure Checkout</button>
                    </main></body></html>
                    """.formatted(scriptUrl, safeOrder, sessionId, HtmlUtils.htmlEscape(amount), safeOrder);
        }

        // Interactive Mastercard Gateway Simulator for Mock Mode
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Mastercard Payment Gateway Services (Mock)</title>
                  <style>
                    * { box-sizing: border-box; }
                    body { font-family: 'Segoe UI', system-ui, sans-serif; background: #071526; color: #f0f4f8; margin: 0; padding: 20px; display: flex; align-items: center; justify-content: center; min-height: 100vh; }
                    .card-container { width: 100%%; max-width: 440px; background: #0E223B; border-radius: 20px; border: 1px solid rgba(255,255,255,0.1); padding: 28px; box-shadow: 0 20px 50px rgba(0,0,0,0.5); position: relative; overflow: hidden; }
                    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; border-bottom: 1px solid rgba(255,255,255,0.08); padding-bottom: 16px; }
                    .mc-logo { display: flex; align-items: center; }
                    .circle-red { width: 28px; height: 28px; background: #EB001B; border-radius: 50%%; }
                    .circle-orange { width: 28px; height: 28px; background: #F79E1B; border-radius: 50%%; margin-left: -12px; opacity: 0.9; }
                    .mc-title { font-size: 14px; font-weight: 700; color: #94A3B8; margin-left: 10px; letter-spacing: 0.5px; }
                    .merchant-badge { background: rgba(15, 157, 138, 0.15); color: #2DD4BF; padding: 4px 10px; border-radius: 20px; font-size: 12px; font-weight: 600; border: 1px solid rgba(45, 212, 191, 0.3); }
                    .amount-box { text-align: center; margin-bottom: 24px; background: rgba(255,255,255,0.03); padding: 16px; border-radius: 12px; }
                    .amount-label { font-size: 12px; color: #94A3B8; text-transform: uppercase; letter-spacing: 1px; }
                    .amount-val { font-size: 32px; font-weight: 800; color: #FFFFFF; margin-top: 4px; }
                    .form-group { margin-bottom: 16px; }
                    .form-group label { display: block; font-size: 12px; font-weight: 600; color: #CBD5E1; margin-bottom: 6px; }
                    .input-field { width: 100%%; padding: 12px 14px; background: #162E4D; border: 1px solid #23436B; border-radius: 8px; color: white; font-size: 15px; font-weight: 500; outline: none; transition: border 0.2s; }
                    .input-field:focus { border-color: #38BDF8; box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.2); }
                    .flex-row { display: flex; gap: 12px; }
                    .quick-btn { background: rgba(56, 189, 248, 0.1); color: #38BDF8; border: 1px dashed rgba(56, 189, 248, 0.4); border-radius: 8px; width: 100%%; padding: 10px; font-weight: 600; font-size: 12px; cursor: pointer; margin-bottom: 20px; }
                    .quick-btn:hover { background: rgba(56, 189, 248, 0.2); }
                    .pay-btn { width: 100%%; padding: 14px; background: linear-gradient(135deg, #EB001B 0%%, #C40016 100%%); color: white; border: none; border-radius: 10px; font-size: 16px; font-weight: 700; cursor: pointer; box-shadow: 0 4px 14px rgba(235, 0, 27, 0.4); }
                    .pay-btn:hover { background: linear-gradient(135deg, #FF1A35 0%%, #EB001B 100%%); }
                    .cancel-link { display: block; text-align: center; color: #64748B; font-size: 13px; margin-top: 14px; text-decoration: none; }
                    .cancel-link:hover { color: #94A3B8; }

                    /* OTP Modal */
                    .modal-overlay { display: none; position: absolute; inset: 0; background: rgba(7, 21, 38, 0.92); backdrop-filter: blur(4px); align-items: center; justify-content: center; padding: 20px; }
                    .modal-box { background: #162E4D; border: 1px solid #23436B; border-radius: 16px; padding: 24px; text-align: center; width: 100%%; }
                    .otp-input { letter-spacing: 12px; font-size: 24px; text-align: center; font-weight: 800; }
                  </style>
                </head>
                <body>
                  <div class="card-container">
                    <div class="header">
                      <div class="mc-logo">
                        <div class="circle-red"></div>
                        <div class="circle-orange"></div>
                        <span class="mc-title">MASTERCARD</span>
                      </div>
                      <span class="merchant-badge">%s</span>
                    </div>

                    <div class="amount-box">
                      <div class="amount-label">Payment Amount</div>
                      <div class="amount-val">₹ %s</div>
                      <div style="font-size: 11px; color: #64748B; margin-top: 4px;">Order Ref: %s</div>
                    </div>

                    <button class="quick-btn" onclick="fillTestCard()">⚡ Auto-fill Mastercard Test Card</button>

                    <form id="paymentForm" onsubmit="showOtpModal(event)">
                      <div class="form-group">
                        <label>Cardholder Name</label>
                        <input type="text" id="cardName" class="input-field" placeholder="e.g. Rahul Sharma" required />
                      </div>

                      <div class="form-group">
                        <label>Card Number</label>
                        <input type="text" id="cardNumber" class="input-field" placeholder="5200 0000 0000 0000" maxlength="19" required />
                      </div>

                      <div class="flex-row">
                        <div class="form-group" style="flex: 1;">
                          <label>Expiry Date</label>
                          <input type="text" id="cardExp" class="input-field" placeholder="MM/YY" maxlength="5" required />
                        </div>
                        <div class="form-group" style="flex: 1;">
                          <label>CVV / CVC</label>
                          <input type="password" id="cardCvv" class="input-field" placeholder="123" maxlength="4" required />
                        </div>
                      </div>

                      <button type="submit" class="pay-btn">Pay ₹ %s</button>
                      <a href="/pay/mastercard/%s/cancel" class="cancel-link">Cancel payment</a>
                    </form>

                    <!-- 3D Secure OTP Modal -->
                    <div id="otpModal" class="modal-overlay">
                      <div class="modal-box">
                        <div style="display:flex;justify-content:center;margin-bottom:12px;">
                          <div class="circle-red" style="width:20px;height:20px;"></div>
                          <div class="circle-orange" style="width:20px;height:20px;margin-left:-8px;"></div>
                        </div>
                        <h3 style="margin:0 0 6px; font-size:16px;">Mastercard Identity Check</h3>
                        <p style="font-size:12px; color:#94A3B8; margin-top:0;">Enter 6-digit OTP sent to registered mobile</p>
                        <div class="form-group" style="margin:16px 0;">
                          <input type="text" id="otpValue" class="input-field otp-input" value="123456" maxlength="6" />
                        </div>
                        <button class="pay-btn" onclick="completePayment()">Submit & Complete Payment</button>
                      </div>
                    </div>

                  </div>

                  <script>
                    function fillTestCard() {
                      document.getElementById('cardName').value = 'Merchant Demo User';
                      document.getElementById('cardNumber').value = '5200 0000 0000 0000';
                      document.getElementById('cardExp').value = '12/28';
                      document.getElementById('cardCvv').value = '888';
                    }

                    function showOtpModal(e) {
                      e.preventDefault();
                      document.getElementById('otpModal').style.display = 'flex';
                    }

                    function completePayment() {
                      location.href = '/pay/mastercard/%s/return';
                    }
                  </script>
                </body>
                </html>
                """.formatted(shopName, amount, safeOrder, amount, safeOrder, safeOrder);
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
