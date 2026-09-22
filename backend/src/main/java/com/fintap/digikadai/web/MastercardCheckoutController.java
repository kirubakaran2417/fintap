package com.fintap.digikadai.web;

import com.fintap.digikadai.config.IntegrationProperties;
import com.fintap.digikadai.domain.Payment;
import com.fintap.digikadai.repo.PaymentRepository;
import com.fintap.digikadai.service.PaymentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

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

        // State-of-the-Art Visa & Mastercard Contactless SoftPOS (NFC Tap on Phone) Simulator
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
                  <title>FinTap SoftPOS · Visa & Mastercard Tap to Pay (NFC)</title>
                  <link rel="preconnect" href="https://fonts.googleapis.com">
                  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@500;600;700;800&family=Plus+Jakarta+Sans:wght@400;500;600;700&family=Fira+Code:wght@400;500&display=swap" rel="stylesheet">
                  <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                      font-family: 'Plus Jakarta Sans', sans-serif;
                      background: #060b13;
                      color: #f1f5f9;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      min-height: 100vh;
                      padding: 16px;
                      user-select: none;
                    }
                    .terminal-wrap {
                      width: 100%%;
                      max-width: 460px;
                      background: #0c1424;
                      border-radius: 24px;
                      border: 1px solid rgba(255, 255, 255, 0.08);
                      box-shadow: 0 25px 60px rgba(0, 0, 0, 0.7);
                      padding: 24px 22px;
                      position: relative;
                      overflow: hidden;
                    }
                    /* Top Header */
                    .terminal-header {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      padding-bottom: 16px;
                      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                    }
                    .schemes-logo {
                      display: flex;
                      align-items: center;
                      gap: 12px;
                    }
                    .visa-badge {
                      font-family: 'Outfit', sans-serif;
                      font-weight: 800;
                      font-size: 18px;
                      font-style: italic;
                      color: #2563eb;
                      letter-spacing: -0.5px;
                      background: rgba(37, 99, 235, 0.12);
                      padding: 3px 8px;
                      border-radius: 6px;
                      border: 1px solid rgba(37, 99, 235, 0.3);
                    }
                    .mc-circles {
                      display: flex;
                      align-items: center;
                    }
                    .mc-circle-red { width: 22px; height: 22px; background: #eb001b; border-radius: 50%%; }
                    .mc-circle-orange { width: 22px; height: 22px; background: #f79e1b; border-radius: 50%%; margin-left: -9px; opacity: 0.95; }
                    .nfc-emv-icon {
                      color: #00e5b7;
                      font-size: 18px;
                      font-weight: 800;
                      margin-left: 2px;
                    }
                    .softpos-badge {
                      font-size: 11px;
                      font-weight: 700;
                      letter-spacing: 0.5px;
                      text-transform: uppercase;
                      color: #00e5b7;
                      background: rgba(0, 229, 183, 0.12);
                      border: 1px solid rgba(0, 229, 183, 0.3);
                      padding: 4px 10px;
                      border-radius: 20px;
                    }
                    /* Amount Display */
                    .amount-container {
                      text-align: center;
                      margin: 20px 0 16px;
                      background: rgba(255, 255, 255, 0.02);
                      border: 1px solid rgba(255, 255, 255, 0.06);
                      padding: 16px;
                      border-radius: 16px;
                    }
                    .shop-name {
                      font-size: 13px;
                      color: #94a3b8;
                      margin-bottom: 4px;
                    }
                    .amount-val {
                      font-family: 'Outfit', sans-serif;
                      font-size: 40px;
                      font-weight: 800;
                      color: #ffffff;
                      letter-spacing: -1px;
                    }
                    .order-id-label {
                      font-size: 11px;
                      color: #64748b;
                      margin-top: 4px;
                      font-family: 'Fira Code', monospace;
                    }

                    /* Tabs */
                    .tab-bar {
                      display: flex;
                      background: #070c17;
                      border-radius: 12px;
                      padding: 4px;
                      margin-bottom: 20px;
                      border: 1px solid rgba(255, 255, 255, 0.05);
                    }
                    .tab-btn {
                      flex: 1;
                      padding: 8px 12px;
                      border: none;
                      background: transparent;
                      color: #94a3b8;
                      font-size: 12px;
                      font-weight: 600;
                      border-radius: 8px;
                      cursor: pointer;
                      transition: all 0.2s;
                    }
                    .tab-btn.active {
                      background: #14213d;
                      color: #00e5b7;
                      border: 1px solid rgba(0, 229, 183, 0.3);
                    }

                    /* NFC Antenna Target */
                    .nfc-target-zone {
                      background: radial-gradient(circle at center, rgba(0, 229, 183, 0.12) 0%%, rgba(12, 20, 36, 0.4) 70%%);
                      border: 2px dashed rgba(0, 229, 183, 0.35);
                      border-radius: 20px;
                      padding: 24px 16px;
                      text-align: center;
                      position: relative;
                      margin-bottom: 20px;
                      cursor: pointer;
                      transition: all 0.3s;
                    }
                    .nfc-target-zone:hover {
                      border-color: #00e5b7;
                      box-shadow: 0 0 25px rgba(0, 229, 183, 0.2);
                    }
                    .nfc-wave-graphic {
                      position: relative;
                      width: 72px;
                      height: 72px;
                      margin: 0 auto 12px;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                    }
                    .nfc-core-icon {
                      width: 48px;
                      height: 48px;
                      border-radius: 50%%;
                      background: linear-gradient(135deg, #00e5b7, #3b82f6);
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      color: #000;
                      font-size: 22px;
                      font-weight: 900;
                      z-index: 2;
                      box-shadow: 0 0 15px rgba(0, 229, 183, 0.5);
                    }
                    .nfc-pulse-ring {
                      position: absolute;
                      border: 2px solid #00e5b7;
                      border-radius: 50%%;
                      width: 100%%;
                      height: 100%%;
                      animation: nfcPulse 2s infinite cubic-bezier(0.2, 0.8, 0.2, 1);
                      opacity: 0;
                    }
                    .nfc-pulse-ring:nth-child(2) { animation-delay: 0.6s; }
                    .nfc-pulse-ring:nth-child(3) { animation-delay: 1.2s; }
                    @keyframes nfcPulse {
                      0%% { transform: scale(0.6); opacity: 0.9; }
                      100%% { transform: scale(1.6); opacity: 0; }
                    }
                    .nfc-title {
                      font-size: 15px;
                      font-weight: 700;
                      color: #fff;
                      margin-bottom: 4px;
                    }
                    .nfc-subtitle {
                      font-size: 12px;
                      color: #94a3b8;
                      line-height: 1.4;
                    }
                    .nfc-status-pill {
                      display: inline-flex;
                      align-items: center;
                      gap: 6px;
                      background: rgba(0, 0, 0, 0.4);
                      border: 1px solid rgba(255, 255, 255, 0.1);
                      border-radius: 20px;
                      padding: 4px 12px;
                      font-size: 11px;
                      color: #38bdf8;
                      margin-top: 10px;
                    }
                    .status-dot {
                      width: 8px;
                      height: 8px;
                      border-radius: 50%%;
                      background: #00e5b7;
                      box-shadow: 0 0 8px #00e5b7;
                    }

                    /* Cards Selection Shelf */
                    .shelf-label {
                      font-size: 11px;
                      text-transform: uppercase;
                      letter-spacing: 0.8px;
                      color: #64748b;
                      margin-bottom: 10px;
                      font-weight: 700;
                    }
                    .cards-grid {
                      display: grid;
                      grid-template-columns: 1fr 1fr;
                      gap: 12px;
                      margin-bottom: 18px;
                    }
                    .card-item {
                      border-radius: 12px;
                      padding: 14px 12px;
                      cursor: pointer;
                      position: relative;
                      overflow: hidden;
                      transition: all 0.25s ease;
                      border: 1px solid rgba(255, 255, 255, 0.1);
                      text-align: left;
                    }
                    .card-item:hover {
                      transform: translateY(-3px) scale(1.02);
                      box-shadow: 0 10px 20px rgba(0, 0, 0, 0.4);
                    }
                    .card-visa {
                      background: linear-gradient(135deg, #0d2040 0%%, #1a3a68 50%%, #0a1b38 100%%);
                      border-color: rgba(37, 99, 235, 0.4);
                    }
                    .card-visa:hover { border-color: #3b82f6; }
                    .card-mc {
                      background: linear-gradient(135deg, #18181b 0%%, #27272a 50%%, #151518 100%%);
                      border-color: rgba(235, 0, 27, 0.4);
                    }
                    .card-mc:hover { border-color: #f79e1b; }

                    .card-chip {
                      width: 24px;
                      height: 18px;
                      background: linear-gradient(135deg, #ffd700, #b8860b);
                      border-radius: 4px;
                      margin-bottom: 8px;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                    }
                    .card-brand-row {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      margin-bottom: 8px;
                    }
                    .card-pan {
                      font-family: 'Fira Code', monospace;
                      font-size: 13px;
                      font-weight: 600;
                      color: #fff;
                      letter-spacing: 1px;
                      margin-bottom: 6px;
                    }
                    .card-type {
                      font-size: 10px;
                      color: #94a3b8;
                      text-transform: uppercase;
                      letter-spacing: 0.5px;
                    }

                    /* Action Tap Button */
                    .tap-btn-row {
                      display: flex;
                      gap: 10px;
                    }
                    .btn-tap {
                      flex: 1;
                      padding: 12px;
                      border: none;
                      border-radius: 10px;
                      font-size: 13px;
                      font-weight: 700;
                      cursor: pointer;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      gap: 6px;
                      transition: all 0.2s;
                    }
                    .btn-tap-visa {
                      background: linear-gradient(135deg, #2563eb, #1d4ed8);
                      color: #fff;
                    }
                    .btn-tap-visa:hover { background: #3b82f6; }
                    .btn-tap-mc {
                      background: linear-gradient(135deg, #eb001b, #f79e1b);
                      color: #fff;
                    }
                    .btn-tap-mc:hover { opacity: 0.92; }

                    /* Live Telemetry Log */
                    .telemetry-box {
                      display: none;
                      margin-top: 16px;
                      background: #070c17;
                      border: 1px solid rgba(0, 229, 183, 0.3);
                      border-radius: 12px;
                      padding: 14px;
                      font-family: 'Fira Code', monospace;
                      font-size: 11px;
                      color: #38bdf8;
                      line-height: 1.6;
                      text-align: left;
                    }

                    /* Processing Overlay */
                    .tap-processing-overlay {
                      display: none;
                      position: absolute;
                      inset: 0;
                      background: rgba(12, 20, 36, 0.95);
                      backdrop-filter: blur(8px);
                      z-index: 50;
                      flex-direction: column;
                      align-items: center;
                      justify-content: center;
                      text-align: center;
                      padding: 24px;
                    }
                    .success-circle {
                      width: 80px;
                      height: 80px;
                      border-radius: 50%%;
                      background: #00e5b7;
                      color: #000;
                      font-size: 42px;
                      font-weight: 900;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      box-shadow: 0 0 30px rgba(0, 229, 183, 0.6);
                      margin-bottom: 20px;
                      animation: successPop 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
                    }
                    @keyframes successPop {
                      0%% { transform: scale(0.3); opacity: 0; }
                      100%% { transform: scale(1); opacity: 1; }
                    }

                    /* Manual Fallback Form */
                    #manualSection { display: none; }
                    .form-group { margin-bottom: 14px; text-align: left; }
                    .form-group label { display: block; font-size: 12px; font-weight: 600; color: #cbd5e1; margin-bottom: 6px; }
                    .input-field {
                      width: 100%%;
                      padding: 12px 14px;
                      background: #070c17;
                      border: 1px solid #1e293b;
                      border-radius: 8px;
                      color: white;
                      font-size: 14px;
                      outline: none;
                    }
                    .input-field:focus { border-color: #00e5b7; }
                    .flex-row { display: flex; gap: 10px; }
                    .btn-submit-manual {
                      width: 100%%;
                      padding: 13px;
                      background: #00e5b7;
                      color: #000;
                      font-weight: 800;
                      font-size: 15px;
                      border: none;
                      border-radius: 10px;
                      cursor: pointer;
                      margin-top: 10px;
                    }
                  </style>
                </head>
                <body>
                  <div class="terminal-wrap">

                    <!-- Header -->
                    <div class="terminal-header">
                      <div class="schemes-logo">
                        <span class="visa-badge">VISA</span>
                        <div class="mc-circles">
                          <div class="mc-circle-red"></div>
                          <div class="mc-circle-orange"></div>
                        </div>
                        <span class="nfc-emv-icon">)))</span>
                      </div>
                      <span class="softpos-badge">SoftPOS Active</span>
                    </div>

                    <!-- Amount -->
                    <div class="amount-container">
                      <div class="shop-name">%s</div>
                      <div class="amount-val">₹ %s</div>
                      <div class="order-id-label">Order: %s</div>
                    </div>

                    <!-- Tab Switcher -->
                    <div class="tab-bar">
                      <button class="tab-btn active" id="tabNfc" onclick="switchTab('nfc')">📡 Contactless NFC Tap</button>
                      <button class="tab-btn" id="tabManual" onclick="switchTab('manual')">⌨️ Manual Card Entry</button>
                    </div>

                    <!-- NFC Tap Section -->
                    <div id="nfcSection">
                      <div class="nfc-target-zone" id="antennaZone" onclick="triggerCardTap('VISA', '4242')">
                        <div class="nfc-wave-graphic">
                          <div class="nfc-pulse-ring"></div>
                          <div class="nfc-pulse-ring"></div>
                          <div class="nfc-pulse-ring"></div>
                          <div class="nfc-core-icon">)))</div>
                        </div>
                        <div class="nfc-title">Hold Card Here to Pay</div>
                        <div class="nfc-subtitle">Hold Visa, Mastercard contactless card or smartphone near top-rear of device</div>
                        <div class="nfc-status-pill" id="nfcStatus">
                          <span class="status-dot"></span>
                          <span id="nfcStatusText">SoftPOS NFC Receiver Ready</span>
                        </div>
                      </div>

                      <div class="shelf-label">Tap Test Contactless Card:</div>
                      <div class="cards-grid">
                        <!-- Visa Card -->
                        <div class="card-item card-visa" onclick="triggerCardTap('VISA', '4242')">
                          <div class="card-brand-row">
                            <div class="card-chip"></div>
                            <span class="nfc-emv-icon" style="color: #60a5fa;">)))</span>
                          </div>
                          <div class="card-pan">•••• 4242</div>
                          <div class="card-type">VISA PLATINUM TAP</div>
                        </div>

                        <!-- Mastercard Card -->
                        <div class="card-item card-mc" onclick="triggerCardTap('MASTERCARD', '5412')">
                          <div class="card-brand-row">
                            <div class="card-chip"></div>
                            <span class="nfc-emv-icon" style="color: #fbbf24;">)))</span>
                          </div>
                          <div class="card-pan">•••• 5412</div>
                          <div class="card-type">MC WORLD ELITE TAP</div>
                        </div>
                      </div>

                      <div class="tap-btn-row">
                        <button class="btn-tap btn-tap-visa" onclick="triggerCardTap('VISA', '4242')">
                          <span>💳 Tap Visa</span>
                        </button>
                        <button class="btn-tap btn-tap-mc" onclick="triggerCardTap('MASTERCARD', '5412')">
                          <span>💳 Tap Mastercard</span>
                        </button>
                      </div>

                      <!-- Telemetry Console -->
                      <div class="telemetry-box" id="telemetryBox"></div>
                    </div>

                    <!-- Manual Card Entry Section (Fallback) -->
                    <div id="manualSection">
                      <form onsubmit="handleManualSubmit(event)">
                        <div class="form-group">
                          <label>Cardholder Name</label>
                          <input type="text" id="manualName" class="input-field" value="Lakshmi Customer" required />
                        </div>
                        <div class="form-group">
                          <label>Card Number</label>
                          <input type="text" id="manualPan" class="input-field" value="5200 0000 0000 5412" maxlength="19" required />
                        </div>
                        <div class="flex-row">
                          <div class="form-group" style="flex: 1;">
                            <label>Expiry</label>
                            <input type="text" id="manualExp" class="input-field" value="12/28" maxlength="5" required />
                          </div>
                          <div class="form-group" style="flex: 1;">
                            <label>CVV</label>
                            <input type="password" id="manualCvv" class="input-field" value="888" maxlength="4" required />
                          </div>
                        </div>
                        <button type="submit" class="btn-submit-manual">Authorize ₹ %s</button>
                      </form>
                    </div>

                    <!-- Processing Overlay -->
                    <div class="tap-processing-overlay" id="overlay">
                      <div class="success-circle">✓</div>
                      <h2 id="overlayTitle" style="font-size: 22px; font-weight: 800; margin-bottom: 8px;">Payment Approved</h2>
                      <p id="overlaySub" style="color: #94a3b8; font-size: 13px; line-height: 1.5;">Contactless EMV Cryptogram Verified.<br/>Authorizing with Bank...</p>
                    </div>

                  </div>

                  <script>
                    const orderId = '%s';
                    const amount = '%s';

                    // Web Audio API POS Terminal Confirmation Beep
                    function playPosBeep() {
                      try {
                        const AudioContext = window.AudioContext || window.webkitAudioContext;
                        if (!AudioContext) return;
                        const ctx = new AudioContext();
                        const now = ctx.currentTime;
                        
                        const osc1 = ctx.createOscillator();
                        const gain1 = ctx.createGain();
                        osc1.frequency.setValueAtTime(880, now);
                        gain1.gain.setValueAtTime(0.2, now);
                        gain1.gain.exponentialRampToValueAtTime(0.01, now + 0.08);
                        osc1.connect(gain1);
                        gain1.connect(ctx.destination);
                        osc1.start(now);
                        osc1.stop(now + 0.08);

                        const osc2 = ctx.createOscillator();
                        const gain2 = ctx.createGain();
                        osc2.frequency.setValueAtTime(1760, now + 0.08);
                        gain2.gain.setValueAtTime(0.25, now + 0.08);
                        gain2.gain.exponentialRampToValueAtTime(0.01, now + 0.22);
                        osc2.connect(gain2);
                        gain2.connect(ctx.destination);
                        osc2.start(now + 0.08);
                        osc2.stop(now + 0.22);
                      } catch (e) {
                        console.log('AudioContext not allowed or not supported:', e);
                      }
                    }

                    // Web Speech Synthesis Audio Announcement
                    function playSpeechPrompt(brand) {
                      try {
                        if ('speechSynthesis' in window) {
                          const utterance = new SpeechSynthesisUtterance('Payment of rupees ' + amount + ' received via ' + brand + ' contactless tap');
                          utterance.rate = 1.0;
                          window.speechSynthesis.speak(utterance);
                        }
                      } catch (e) {}
                    }

                    // Physical Hardware NFC Reader Scan (Supported on Android Chrome)
                    async function initHardwareNfc() {
                      if ('NDEFReader' in window) {
                        try {
                          const ndef = new NDEFReader();
                          await ndef.scan();
                          document.getElementById('nfcStatusText').textContent = 'Phone NFC Hardware Active';
                          ndef.onreading = event => {
                            triggerCardTap('VISA', '4242');
                          };
                        } catch (err) {
                          document.getElementById('nfcStatusText').textContent = 'SoftPOS Receiver Active';
                        }
                      }
                    }

                    // Card Tap Trigger Action
                    function triggerCardTap(brand, last4) {
                      // 1. Audio Sound FX & Haptic Vibration
                      playPosBeep();
                      if (navigator.vibrate) {
                        navigator.vibrate([100, 50, 100]);
                      }

                      // 2. Display EMV Telemetry
                      const telem = document.getElementById('telemetryBox');
                      telem.style.display = 'block';
                      telem.innerHTML = `
                        <strong>[EMVCo Contactless SoftPOS Telemetry]</strong><br/>
                        &bull; Scheme: ` + (brand === 'VISA' ? 'VISA PayWave (qVSDC)' : 'Mastercard PayPass (M/Chip)') + `<br/>
                        &bull; AID: ` + (brand === 'VISA' ? 'A0000000031010' : 'A0000000041010') + `<br/>
                        &bull; Transmission: ISO/IEC 14443 Type A (13.56 MHz NFC)<br/>
                        &bull; Masked PAN: &bull;&bull;&bull;&bull; &bull;&bull;&bull;&bull; &bull;&bull;&bull;&bull; ` + last4 + `<br/>
                        &bull; Cryptogram: ARQC 0x` + Math.random().toString(16).substr(2, 8).toUpperCase() + ` (Authorized)<br/>
                        &bull; CVM: No PIN Required (Below ₹5,000 Contactless Limit)
                      `;

                      // 3. Show Processing Overlay
                      const overlay = document.getElementById('overlay');
                      overlay.style.display = 'flex';
                      document.getElementById('overlayTitle').textContent = brand + ' Tap Approved';
                      document.getElementById('overlaySub').innerHTML = 'Payment of ₹ ' + amount + ' Approved.<br/>Settling with FinTap Kadai...';

                      playSpeechPrompt(brand);

                      // 4. Redirect to return endpoint after confirmation
                      setTimeout(() => {
                        window.location.href = '/pay/mastercard/' + orderId + '/return?brand=' + brand;
                      }, 1600);
                    }

                    function switchTab(mode) {
                      document.getElementById('tabNfc').classList.toggle('active', mode === 'nfc');
                      document.getElementById('tabManual').classList.toggle('active', mode === 'manual');
                      document.getElementById('nfcSection').style.display = mode === 'nfc' ? 'block' : 'none';
                      document.getElementById('manualSection').style.display = mode === 'manual' ? 'block' : 'none';
                    }

                    function handleManualSubmit(e) {
                      e.preventDefault();
                      triggerCardTap('MASTERCARD', '5412');
                    }

                    initHardwareNfc();
                  </script>
                </body>
                </html>
                """.formatted(shopName, amount, safeOrder, amount, safeOrder, amount);
    }

    @GetMapping(value = "/pay/mastercard/{orderId}/return", produces = MediaType.TEXT_HTML_VALUE)
    public String returned(@PathVariable String orderId,
                           @RequestParam(required = false, defaultValue = "VISA") String brand) {
        var payment = paymentService.reconcileMastercard(orderId, brand);
        String amount = payment.amount() == null ? "0.00" : payment.amount().toPlainString();
        return resultPage(payment.status().name(), orderId, brand, amount);
    }

    @GetMapping(value = "/pay/mastercard/{orderId}/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public String cancelled(@PathVariable String orderId) {
        var payment = paymentService.cancelMastercard(orderId);
        String amount = payment.amount() == null ? "0.00" : payment.amount().toPlainString();
        return resultPage(payment.status().name(), orderId, "CARD", amount);
    }

    private Payment requireMastercardPayment(String orderId) {
        Payment payment = payments.findByGatewayOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        if (!"MASTERCARD".equals(payment.getGatewayProvider()) || payment.getGatewaySessionId() == null) {
            throw new IllegalArgumentException("Mastercard checkout is not available for this payment");
        }
        return payment;
    }

    private String resultPage(String status, String orderId, String brand, String amount) {
        boolean ok = "SUCCESS".equalsIgnoreCase(status) || "CAPTURED".equalsIgnoreCase(status);
        String brandLabel = "MASTERCARD".equalsIgnoreCase(brand) ? "Mastercard Contactless Tap" : "Visa Contactless Tap";
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>FinTap · Payment %s</title>
                  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@600;700;800&family=Plus+Jakarta+Sans:wght@400;500;600&family=Fira+Code&display=swap" rel="stylesheet">
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
                      background: %s;
                      color: #000;
                      font-size: 38px;
                      font-weight: 900;
                      margin: 0 auto 20px;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      box-shadow: 0 0 30px %s;
                    }
                    h1 { font-family: 'Outfit', sans-serif; font-size: 26px; font-weight: 800; margin-bottom: 6px; }
                    .amount-display { font-family: 'Outfit', sans-serif; font-size: 42px; font-weight: 800; color: #00e5b7; margin: 16px 0; }
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
                    .btn-done:hover { background: #34d399; }
                  </style>
                </head>
                <body>
                  <div class="receipt-card">
                    <div class="icon-circle">%s</div>
                    <h1>%s</h1>
                    <div class="amount-display">₹ %s</div>
                    
                    <div class="detail-row">
                      <span>Payment Method</span>
                      <span>%s</span>
                    </div>
                    <div class="detail-row">
                      <span>Order Reference</span>
                      <span style="font-family: 'Fira Code', monospace; font-size: 12px;">%s</span>
                    </div>
                    <div class="detail-row">
                      <span>Status</span>
                      <span style="color: %s;">%s</span>
                    </div>

                    <button class="btn-done" onclick="window.close(); if(!window.closed) window.history.back();">Return to FinTap</button>
                  </div>
                </body>
                </html>
                """.formatted(
                HtmlUtils.htmlEscape(status),
                ok ? "#00e5b7" : "#ef4444",
                ok ? "rgba(0, 229, 183, 0.4)" : "rgba(239, 68, 68, 0.4)",
                ok ? "✓" : "✕",
                ok ? "Contactless Tap Approved" : "Payment " + HtmlUtils.htmlEscape(status),
                HtmlUtils.htmlEscape(amount),
                HtmlUtils.htmlEscape(brandLabel),
                HtmlUtils.htmlEscape(orderId),
                ok ? "#00e5b7" : "#ef4444",
                HtmlUtils.htmlEscape(status)
        );
    }
}
